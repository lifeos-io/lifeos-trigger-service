package com.lifeos.trigger.account.service;

import java.util.UUID;

/** KVKK: on {@code UserDeletionRequested}, Trigger forgets everything it holds for the user. */
public interface AccountDeletionService {

    void deleteAll(UUID userId);
}
