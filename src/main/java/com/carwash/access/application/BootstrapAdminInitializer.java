package com.carwash.access.application;

import com.carwash.identity.application.UserManagementService;
import com.carwash.identity.application.CreateUserCommand;
import com.carwash.identity.domain.RoleName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {
    private final UserManagementService users;
    private final boolean enabled;
    private final String id, name, email, phone, password;

    public BootstrapAdminInitializer(UserManagementService users,
                                     @Value("${carwash.security.bootstrap-admin.enabled:false}") boolean enabled,
                                     @Value("${carwash.security.bootstrap-admin.user-id:}") String id,
                                     @Value("${carwash.security.bootstrap-admin.full-name:}") String name,
                                     @Value("${carwash.security.bootstrap-admin.email:}") String email,
                                     @Value("${carwash.security.bootstrap-admin.phone:}") String phone,
                                     @Value("${carwash.security.bootstrap-admin.password:}") String password) {
        this.users=users; this.enabled=enabled; this.id=id; this.name=name; this.email=email; this.phone=phone; this.password=password;
    }
    @Override public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (id.isBlank() || name.isBlank() || email.isBlank() || phone.isBlank() || password.isBlank())
            throw new IllegalStateException("All bootstrap administrator properties are required when enabled");
        users.createUser(new CreateUserCommand(id, name, email, phone, password));
        users.assignRole(id, RoleName.PLATFORM_ADMIN);
    }
}
