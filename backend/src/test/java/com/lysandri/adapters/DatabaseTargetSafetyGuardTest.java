package com.lysandri.adapters;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseTargetSafetyGuardTest {

    @Test
    void rejectsProductionPort5432InVariousFormats() {
        assertThrows(SecurityException.class, () ->
                DatabaseTargetSafetyGuard.validateTarget("jdbc:postgresql://localhost:5432/lysandri_clone_db", "d27359376c51"));
        assertThrows(SecurityException.class, () ->
                DatabaseTargetSafetyGuard.validateTarget("jdbc:postgresql://127.0.0.1:5432/test", "d27359376c51"));
        assertThrows(SecurityException.class, () ->
                DatabaseTargetSafetyGuard.validateTarget("jdbc:postgresql://prod.internal:5432/any_db?ssl=true", "d27359376c51"));
        assertThrows(SecurityException.class, () ->
                DatabaseTargetSafetyGuard.validateTarget("jdbc:postgresql://db:5432", "d27359376c51"));
    }

    @Test
    void rejectsProductionDatabaseLysandriDbInVariousFormats() {
        assertThrows(SecurityException.class, () ->
                DatabaseTargetSafetyGuard.validateTarget("jdbc:postgresql://localhost:5434/lysandri_db", "d27359376c51"));
        assertThrows(SecurityException.class, () ->
                DatabaseTargetSafetyGuard.validateTarget("jdbc:postgresql://localhost:5435/lysandri_db?ssl=false", "d27359376c51"));
        assertThrows(SecurityException.class, () ->
                DatabaseTargetSafetyGuard.validateTarget("jdbc:postgresql://127.0.0.1:5434/lysandri_db/", "d27359376c51"));
    }

    @Test
    void rejectsOriginalProductionContainerIdAndName() {
        assertThrows(SecurityException.class, () ->
                DatabaseTargetSafetyGuard.validateTarget("jdbc:postgresql://localhost:5434/lysandri_clone_db", "d3e0bb55ec61288a2b15f9a8a0243afc2197faa9083afb0add29be42bb676143"));
        assertThrows(SecurityException.class, () ->
                DatabaseTargetSafetyGuard.validateTarget("jdbc:postgresql://localhost:5434/lysandri_clone_db", "lysandri-postgres"));
    }

    @Test
    void rejectsNullOrEmptyUrls() {
        assertThrows(SecurityException.class, () ->
                DatabaseTargetSafetyGuard.validateTarget(null, "d27359376c51"));
        assertThrows(SecurityException.class, () ->
                DatabaseTargetSafetyGuard.validateTarget("   ", "d27359376c51"));
    }

    @Test
    void rejectsForbiddenFlywayOrDatasourceInValidateUrls() {
        assertThrows(SecurityException.class, () ->
                DatabaseTargetSafetyGuard.validateUrls("jdbc:postgresql://127.0.0.1:5434/lysandri_clone_db", "jdbc:postgresql://127.0.0.1:5432/lysandri_clone_db"));
        assertThrows(SecurityException.class, () ->
                DatabaseTargetSafetyGuard.validateUrls("jdbc:postgresql://127.0.0.1:5434/lysandri_db", "jdbc:postgresql://127.0.0.1:5434/lysandri_clone_db"));
    }

    @Test
    void acceptsValidIsolatedTargets() {
        assertDoesNotThrow(() ->
                DatabaseTargetSafetyGuard.validateTarget("jdbc:postgresql://127.0.0.1:5434/lysandri_clone_db", "d27359376c51"));
        assertDoesNotThrow(() ->
                DatabaseTargetSafetyGuard.validateTarget("jdbc:postgresql://127.0.0.1:5435/lysandri_verify_db", "a44d5d8c80bd"));
        assertDoesNotThrow(() ->
                DatabaseTargetSafetyGuard.validateUrls("jdbc:postgresql://127.0.0.1:5434/lysandri_clone_db", "jdbc:postgresql://127.0.0.1:5434/lysandri_clone_db"));
    }

    @Test
    void acceptsValidCloneAndDisposableCloneTargets() {
        assertDoesNotThrow(() ->
                DatabaseTargetSafetyGuard.validateCloneJdbcUrl("jdbc:postgresql://127.0.0.1:5434/lysandri_clone_db", "CloneDB"));
        assertDoesNotThrow(() ->
                DatabaseTargetSafetyGuard.validateCloneJdbcUrl("jdbc:postgresql://127.0.0.1:5434/lysandri_clone_disposable_test", "DisposableCloneDB"));
        assertThrows(SecurityException.class, () ->
                DatabaseTargetSafetyGuard.validateCloneJdbcUrl("jdbc:postgresql://127.0.0.1:5434/unauthorized_db", "UnauthorizedDB"));
        assertThrows(SecurityException.class, () ->
                DatabaseTargetSafetyGuard.validateCloneJdbcUrl("jdbc:postgresql://127.0.0.1:5432/lysandri_clone_db", "ForbiddenProdPort"));
    }
}
