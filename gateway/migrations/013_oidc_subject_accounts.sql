-- Subject-only identities need distinct accounts without fabricating an email.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
UPDATE users SET email = NULL WHERE email = '';
