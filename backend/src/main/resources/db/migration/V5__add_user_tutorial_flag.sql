ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS tutorial_completed BOOLEAN NOT NULL DEFAULT false;

-- Existing accounts are considered already onboarded while newly registered users default to false.
UPDATE user_profiles
SET tutorial_completed = true
WHERE tutorial_completed = false;
