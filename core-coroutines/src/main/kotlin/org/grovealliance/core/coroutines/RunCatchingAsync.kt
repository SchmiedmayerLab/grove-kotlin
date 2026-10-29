//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.core.coroutines

import kotlinx.coroutines.CancellationException

/**
 * Returns [block]'s value or failure, propagating coroutine cancellation to the caller.
 */
@Suppress("detekt:TooGenericExceptionCaught")
suspend inline fun <T> runCatchingAsync(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}
