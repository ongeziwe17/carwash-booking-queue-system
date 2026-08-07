package com.carwash.testsupport;

import com.carwash.api.dto.CreateUserRequest;

public final class UserFixtureBuilder {

    public static final String DEFAULT_PASSWORD = "LocalTestPassword123!";

    private String userId;
    private String fullName = "Integration Test User";
    private String email;
    private String phone = "0821234567";
    private String password = DEFAULT_PASSWORD;

    private UserFixtureBuilder() {
    }

    public static UserFixtureBuilder valid(TestIdFactory ids) {
        String id = ids.user();
        UserFixtureBuilder builder = new UserFixtureBuilder();
        builder.userId = id;
        builder.email = ids.emailFor(id);
        return builder;
    }

    public UserFixtureBuilder userId(String value) { this.userId = value; return this; }
    public UserFixtureBuilder fullName(String value) { this.fullName = value; return this; }
    public UserFixtureBuilder email(String value) { this.email = value; return this; }
    public UserFixtureBuilder phone(String value) { this.phone = value; return this; }
    public UserFixtureBuilder password(String value) { this.password = value; return this; }

    public CreateUserRequest build() {
        return new CreateUserRequest(userId, fullName, email, phone, password);
    }
}
