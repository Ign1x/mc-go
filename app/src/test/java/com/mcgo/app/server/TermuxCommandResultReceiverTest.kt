package com.mcgo.app.server

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class TermuxCommandResultReceiverTest {
    @Test
    fun classifyTermuxResultStatus_marksMissingOrErroredResultsAsFailed() {
        assertThat(
            classifyTermuxResultStatus(
                resultMissing = true,
                errCode = null,
                exitCode = null,
                errmsg = "",
            ),
        ).isEqualTo(PaperServerEventStatus.Failed)

        assertThat(
            classifyTermuxResultStatus(
                resultMissing = false,
                errCode = 1,
                exitCode = null,
                errmsg = "allow-external-apps is disabled",
            ),
        ).isEqualTo(PaperServerEventStatus.Failed)

        assertThat(
            classifyTermuxResultStatus(
                resultMissing = false,
                errCode = 0,
                exitCode = 127,
                errmsg = "",
            ),
        ).isEqualTo(PaperServerEventStatus.Failed)
    }

    @Test
    fun classifyTermuxResultStatus_acceptsNormalStopCodes() {
        listOf(0, 130, 137, 143).forEach { exitCode ->
            assertThat(
                classifyTermuxResultStatus(
                    resultMissing = false,
                    errCode = 0,
                    exitCode = exitCode,
                    errmsg = "",
                ),
            ).isEqualTo(PaperServerEventStatus.Stopped)
        }
    }
}
