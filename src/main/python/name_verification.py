"""
Name Verification Service - Core validation logic

A service for verifying names against datasets and dictionaries.
Provides decision recommendations based on name legitimacy analysis.

Author: AI Engine Team
Version: 1.0.0
"""

import json
import re
import logging
import sys
from pathlib import Path
from typing import Dict, List, Any
from dataclasses import dataclass
from datetime import datetime
from names_dataset import NameDataset


class NameVerificationConfig:
    """Configuration class for Name Verification Service"""
    
    def __init__(self):
        self.base_path = Path(__file__).parent
        self.dictionary_path = self.base_path / "name_model" / "words_dictionary.json"
        self.log_level = logging.INFO
        self.log_format = "%(asctime)s - %(name)s - %(levelname)s - %(message)s"


@dataclass
class NameVerificationRequest:
    """Request model for name verification"""
    name: str
    timestamp: str = None
    
    def __post_init__(self):
        if not self.timestamp:
            self.timestamp = datetime.now().isoformat()


@dataclass
class NameVerificationResponse:
    """Simplified response model"""
    decision: str
    reasons: List[str]
    notes: Dict[str, Any]


class NameVerificationService:
    """
    Name Verification Service
    
    Provides name verification capabilities by analyzing names against:
    - Names dataset for legitimacy
    - Dictionary words for common English words
    - Pattern analysis for suspicious content
    """
    
    def __init__(self, config: NameVerificationConfig = None):
        self.config = config or NameVerificationConfig()
        self.logger = logging.getLogger(__name__)
        self.name_dataset = NameDataset()
        self.dictionary = self._load_dictionary()

    def _load_dictionary(self) -> Dict[str, int]:
        """Load the English words dictionary"""
        try:
            if not self.config.dictionary_path.exists():
                self.logger.warning(f"Dictionary file not found at {self.config.dictionary_path}")
                return {}
            
            with open(self.config.dictionary_path, 'r', encoding='utf-8') as f:
                dictionary = json.load(f)
            
            self.logger.info(f"Dictionary loaded with {len(dictionary)} words")
            return dictionary
            
        except Exception as e:
            self.logger.error(f"Failed to load dictionary: {str(e)}")
            return {}
    
    def _analyze_name_patterns(self, name: str) -> Dict[str, Any]:
        """
        Analyze name patterns for suspicious elements while accounting for legitimate conventions
        
        Args:
            name (str): Name to analyze
            
        Returns:
            Dict[str, Any]: Analysis results with metrics and reasons
        """
        self.logger.debug(f"Analyzing patterns for name: {name}")
        
        has_digits = False
        has_special_chars = False
        has_suspicious_patterns = False
        reasons = []
        legitimate_notes = []
        
        if not name or not isinstance(name, str):
            reasons.append("Invalid name format")
            return {
                "has_digits": has_digits,
                "has_special_chars": has_special_chars,
                "has_suspicious_patterns": has_suspicious_patterns,
                "reasons": reasons,
                "legitimate_notes": legitimate_notes
            }
        
        # Legitimate special characters in names across cultures
        legitimate_chars = {
            "'": "apostrophe (e.g., O'Connor)",
            "-": "hyphen (e.g., Smith-Johnson)",
            ".": "abbreviated title (e.g., Dr.)",
            " ": "space",
            "é": "accent mark", "è": "accent mark", "í": "accent mark",
            "ñ": "tilde", "ü": "umlaut", "ö": "umlaut",
            "ç": "cedilla", "ø": "nordic character", "å": "nordic character",
            "á": "accent mark", "à": "accent mark", "ó": "accent mark"
        }
        
        # Suspicious patterns
        suspicious_patterns = [
            (r'\d', "digits"),
            (r'(.)\1{3,}', "repeated characters (4+ times)"),
            (r'[A-Za-z]\d{2,}', "single letter followed by multiple digits"),
            (r'^[xyz]{2,}$', "repeated xyz characters"),
            (r'qwerty|asdf|zxcv|test|xxx|abc|zzz|user|null', "keyboard patterns")
        ]
        
        # Check for special characters
        for char in name:
            if not char.isalnum() and not char.isspace():
                if char in legitimate_chars:
                    legitimate_notes.append(f"Contains legitimate {legitimate_chars[char]}")
                else:
                    has_special_chars = True
        
        if has_special_chars:
            reasons.append("Contains unusual special characters")
        
        # Check for digits
        if re.search(r'\d', name):
            has_digits = True
            reasons.append("Contains numerical digits")
        
        # Additional suspicious heuristics: length, consonant clusters, junk tokens
        # Too long
        if len(name) > 50:
            has_suspicious_patterns = True
            reasons.append("Name length unusually long")
        # Too short
        if len(name) < 2:
            has_suspicious_patterns = True
            reasons.append("Name too short")
        # Unnatural consonant clustering (5+ consonants in a row)
        if re.search(r'[^aeiouAEIOU]{5,}', name):
            has_suspicious_patterns = True
            reasons.append("Unnatural consonant cluster")
        
        # Repeated common “junk” tokens
        junk_tokens = ["test", "xxx", "abc", "zzz", "user", "name", "null"]
        if any(tok in name.lower() for tok in junk_tokens):
            has_suspicious_patterns = True
            reasons.append("Contains junk placeholder token")
        
        # Titles (honorifics) that should be treated as suspicious when present
        title_tokens = ["mr.", "mister", "mrs.", "ms.", "miss", "dr.", "prof.", "sir", "madam", "sr", "jr"]
        lower_name = name.lower().replace(".", "")
        for t in title_tokens:
            if re.search(rf'\b{re.escape(t)}\b', lower_name):
                has_suspicious_patterns = True
                reasons.append(f"Contains title '{t}'")
                break
        
        # Check for suspicious patterns
        for pattern, description in suspicious_patterns:
            if re.search(pattern, name, re.IGNORECASE):
                has_suspicious_patterns = True
                reasons.append(f"Contains {description}")
                break
        
        return {
            "has_digits": has_digits,
            "has_special_chars": has_special_chars,
            "has_suspicious_patterns": has_suspicious_patterns,
            "reasons": reasons,
            "legitimate_notes": legitimate_notes
        }

    def _is_empty_side(self, d) -> bool:
        """
        Treat a dataset 'side' (first_name / last_name) as empty when the side
        is falsy or all of its top-level sub-maps/values are falsy.
        """
        if not d:
            return True
        # treat side as empty if all sub-maps are empty/None
        for v in d.values():
            if v:
                return False
        return True
    
    def verify_name(self, request: NameVerificationRequest) -> NameVerificationResponse:
        """
        Verify a name and provide decision recommendation
        
        Args:
            request (NameVerificationRequest): Verification request
            
        Returns:
            NameVerificationResponse: Verification results with decision
        """
        self.logger.info(f"Processing name verification for: {request.name}")
        
        try:
            name = request.name.strip() if request.name else ""
            
            # Handle empty names
            if not name:
                return NameVerificationResponse(
                    decision="REJECT",
                    reasons=["Empty name provided"],
                    notes={"error": "No name to verify"}
                )

            # Search in names dataset
            raw_data = self.name_dataset.search(name) or {}
            fn = raw_data.get("first_name", {})
            ln = raw_data.get("last_name", {})
            
            # Normalize possible None/non-dict sides to empty dicts so .get() is safe
            if not isinstance(fn, dict):
                fn = {}
            if not isinstance(ln, dict):
                ln = {}
            
            # Check if name exists in dataset
            name_in_dataset = raw_data and not (
                self._is_empty_side(fn) and 
                self._is_empty_side(ln)
            )
            
            # Check if name is in dictionary
            is_dictionary_word = name.lower() in self.dictionary
            
            # Analyze name patterns
            pattern_analysis = self._analyze_name_patterns(name)
            
            # Check for suspicious content
            has_suspicious_content = (
                pattern_analysis["has_digits"] or
                pattern_analysis["has_special_chars"] or
                pattern_analysis["has_suspicious_patterns"]
            )

            # --- add top-item / confidence computation for notes ---
            def top_item(d):
                if not d:
                    return (None, 0.0)
                # convert possible None probabilities to 0.0
                items = [(k, (v or 0.0)) for k, v in d.items()]
                if not items:
                    return (None, 0.0)
                key, val = max(items, key=lambda kv: kv[1])
                try:
                    return (key, float(val))
                except Exception:
                    return (key, 0.0)

            fg, fg_prob = top_item(fn.get("gender", {}))
            fc, fc_prob = top_item(fn.get("country", {}))
            lg, lg_prob = top_item(ln.get("gender", {}))
            lc, lc_prob = top_item(ln.get("country", {}))

            # Determine which side gives the strongest signal
            first_signal = max(fg_prob, fc_prob)
            last_signal = max(lg_prob, lc_prob)
            if first_signal >= last_signal:
                best_type = "firstname"
                best_value = fn
                best_top = {"gender": fg, "gender_prob": fg_prob, "country": fc, "country_prob": fc_prob}
            else:
                best_type = "lastname"
                best_value = ln
                best_top = {"gender": lg, "gender_prob": lg_prob, "country": lc, "country_prob": lc_prob}

            # Confidence heuristic: take the strongest single probability observed (percent)
            confidence_rate = int(round(max(fg_prob, fc_prob, lg_prob, lc_prob) * 100))

            # Apply decision logic - map to Java expected values
            if has_suspicious_content:
                decision = "REJECT"
                reasons = pattern_analysis["reasons"]
                self.logger.info(f"Name '{name}' rejected due to suspicious patterns")
            elif name_in_dataset:
                decision = "ACCEPT"
                reasons = ["name found in dataset"]
                self.logger.info(f"Name '{name}' accepted - found in dataset")
            else:
                if not is_dictionary_word:
                    decision = "REVIEW"
                    reasons = ["name not found in dataset", "not a common English word"]
                    self.logger.info(f"Name '{name}' needs review - not in dataset or dictionary")
                else:
                    decision = "REJECT"
                    reasons = ["name not found in dataset", "is a dictionary word"]
                    self.logger.info(f"Name '{name}' rejected - dictionary word but not in dataset")
            
            # Build response notes
            notes = {
                "dictionary_word": "yes" if is_dictionary_word else "no",
                "dataset": "found" if name_in_dataset else "not found",
                "has_digits": pattern_analysis["has_digits"],
                "has_special_chars": pattern_analysis["has_special_chars"],
                "has_suspicious_patterns": pattern_analysis["has_suspicious_patterns"],
                # add top gender/country and confidence info
                "top_gender": {
                    "gender": best_top.get("gender"),
                    "probability_percent": round(best_top.get("gender_prob", 0.0) * 100, 1)
                },
                "top_country": {
                    "country": best_top.get("country"),
                    "probability_percent": round(best_top.get("country_prob", 0.0) * 100, 1)
                },
                "best_match_type": best_type,
                "confidence_rate": confidence_rate
            }
            
            if pattern_analysis["legitimate_notes"]:
                notes["legitimate_chars_found"] = pattern_analysis["legitimate_notes"]
            
            return NameVerificationResponse(
                decision=decision,
                reasons=reasons,
                notes=notes
            )

        except Exception as e:
            self.logger.error(f"Error processing name: {str(e)}", exc_info=True)
            return NameVerificationResponse(
                decision="ERROR",
                reasons=[f"Processing error: {str(e)}"],
                notes={"error": True}
            )

def process_name_verification():
    """Process name verification from command line"""
    try:
        if len(sys.argv) < 3:
            result = {
                "decision": "ERROR",
                "reasons": ["Insufficient arguments"],
                "notes": {"error": "Invalid arguments"}
            }
            print(json.dumps(result))
            return

        firstname = sys.argv[1].strip()
        lastname = sys.argv[2].strip()

        service = NameVerificationService()
        
        # Verify both names
        first_result = service.verify_name(NameVerificationRequest(name=firstname))
        last_result = service.verify_name(NameVerificationRequest(name=lastname))

        # Combine results with the most restrictive decision
        decision_priority = {"ERROR": 4, "REJECT": 3, "REVIEW": 2, "ACCEPT": 1}
        
        overall_decision = first_result.decision if (
            decision_priority[first_result.decision] > decision_priority[last_result.decision]
        ) else last_result.decision

        result = {
            "decision": overall_decision,
            "reasons": [
                *[f"firstname: {r}" for r in first_result.reasons],
                *[f"lastname: {r}" for r in last_result.reasons]
            ],
            "notes": {
                "firstname": first_result.notes,
                "lastname": last_result.notes
            }
        }

        print(json.dumps(result))

    except Exception as e:
        print(json.dumps({
            "decision": "ERROR",
            "reasons": [str(e)],
            "notes": {"error": True}
        }))

if __name__ == "__main__":
    process_name_verification()