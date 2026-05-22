package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFailsWith

class AuthorizedServerDirectoryFailureRecoveryTest {
    @Test
    fun failureCleanupAttemptsBothTargetsAndSwallowsCleanupAndLogFailures() {
        runBlocking {
            val attemptedTargets = mutableListOf<String>()
            val loggedFailures = mutableListOf<Pair<String, String>>()

            runNewModpackServerImportFailureCleanup(
                recovery = NewModpackServerImportFailureRecovery(
                    keepServerEntry = false,
                    deletePrivateWorkspace = true,
                    deleteAuthorizedWorkspace = true,
                ),
                deletePrivateWorkspace = {
                    attemptedTargets += "privateWorkspace"
                    error("private cleanup failed")
                },
                deleteAuthorizedWorkspace = {
                    attemptedTargets += "authorizedWorkspace"
                    error("authorized cleanup failed")
                },
                logCleanupFailure = { cleanupTarget: String, cleanupError: Throwable ->
                    loggedFailures += cleanupTarget to (cleanupError.message ?: "")
                    if (cleanupTarget == "privateWorkspace") {
                        error("cleanup log failed")
                    }
                },
            )

            assertThat(attemptedTargets).containsExactly("privateWorkspace", "authorizedWorkspace").inOrder()
            assertThat(loggedFailures).containsExactly(
                "privateWorkspace" to "private cleanup failed",
                "authorizedWorkspace" to "authorized cleanup failed",
            ).inOrder()
        }
    }

    @Test
    fun failureCleanupRethrowsCoroutineCancellation() {
        runBlocking {
            val cancellation = assertFailsWith<CancellationException> {
                runNewModpackServerImportFailureCleanup(
                    recovery = NewModpackServerImportFailureRecovery(
                        keepServerEntry = false,
                        deletePrivateWorkspace = true,
                        deleteAuthorizedWorkspace = true,
                    ),
                    deletePrivateWorkspace = {
                        throw CancellationException("cancel cleanup")
                    },
                    deleteAuthorizedWorkspace = {
                        error("authorized cleanup should not run after cancellation")
                    },
                    logCleanupFailure = { _, _ ->
                        error("cleanup cancellation should not be logged as recoverable failure")
                    },
                )
            }

            assertThat(cancellation).hasMessageThat().contains("cancel cleanup")
        }
    }

    @Test
    fun failureCleanupRethrowsCoroutineCancellationFromCleanupLogger() {
        runBlocking {
            val attemptedTargets = mutableListOf<String>()
            val cancellation = assertFailsWith<CancellationException> {
                runNewModpackServerImportFailureCleanup(
                    recovery = NewModpackServerImportFailureRecovery(
                        keepServerEntry = false,
                        deletePrivateWorkspace = true,
                        deleteAuthorizedWorkspace = true,
                    ),
                    deletePrivateWorkspace = {
                        attemptedTargets += "privateWorkspace"
                        error("private cleanup failed")
                    },
                    deleteAuthorizedWorkspace = {
                        attemptedTargets += "authorizedWorkspace"
                    },
                    logCleanupFailure = { _, _ ->
                        throw CancellationException("cancel cleanup log")
                    },
                )
            }

            assertThat(cancellation).hasMessageThat().contains("cancel cleanup log")
            assertThat(attemptedTargets).containsExactly("privateWorkspace")
        }
    }
}
