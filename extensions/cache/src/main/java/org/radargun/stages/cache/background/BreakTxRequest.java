package org.radargun.stages.cache.background;

/**
 * Not a "problem" exception, but signalizes that we need to commit current transaction
 * and retry the currently executed operation in a new transaction.
 */
class BreakTxRequest extends Exception {
}
