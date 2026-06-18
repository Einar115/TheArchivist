CREATE TABLE user_roles(
    user_id INT NOT NULL,
    role_id INT NOT NULL,

    PRIMARY KEY (user_id, role_id),

    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE
);


--- Create index for inverse searches (role -> users)
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);