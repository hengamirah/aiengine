
/**This class is a data container (using Lombok's @Data) for storing comparison results:
- Stores results from face and signature comparisons
= Tracks paths to original files used in comparisons
- Used by VerificationController to collect and return comparison results
- Boolean flags indicate quick match status
*/

package com.example.util;

import java.util.Map;

import lombok.Data;

@Data
public class ComparisonResults {
    private Map<String, Object> faceComparison;
    private Map<String, Object> signatureComparison;
    private boolean facesMatch = false;
    private boolean signaturesMatch = false;
    private String idSignaturePath;
    private String uploadedSignaturePath;
    private String idPersonPath;
    private String uploadedPersonPath;

}