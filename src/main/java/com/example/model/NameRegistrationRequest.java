package com.example.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NameRegistrationRequest {

    // accept "firstname", "firstName", "first_name"
    @JsonAlias({ "firstname", "firstName", "first_name" })
    private String firstname;

    // accept "lastname", "lastName", "last_name"
    @JsonAlias({ "lastname", "lastName", "last_name" })
    private String lastname;

    // service expects getDob() — use "dob" field and accept "DOB", "dateOfBirth", etc.
    @JsonAlias({ "dob", "DOB", "dateOfBirth", "date_of_birth" })
    private String dob;
}
