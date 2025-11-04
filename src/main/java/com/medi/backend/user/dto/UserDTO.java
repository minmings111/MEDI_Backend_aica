package com.medi.backend.user.dto;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "password")
public class UserDTO {
    private Integer id;
    private String email;

    @JsonIgnore
    private String password;

    private String name;
    private String phone;
    private Boolean isTermsAgreed;
    private String role;
    private String createdAt;
    private String updatedAt;
}