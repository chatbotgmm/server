package org.dpdns.gmm;

/** Joins independent startup reads; never caches account data or sends game commands. */
final class InitialReadBarrier<T> {
  interface Result<T> { void done(T state, T key, String error, boolean keyError); }
  private final Result<T> result;
  private boolean stateReady, keyReady, finished;
  private T state, key;
  private String stateError, keyError;

  InitialReadBarrier(boolean needsKey, Result<T> result) {
    keyReady = !needsKey;
    this.result = result;
  }

  void state(T value, String error) { accept(value, error, false); }
  void key(T value, String error) { accept(value, error, true); }

  private void accept(T value, String error, boolean fromKey) {
    if (finished || (fromKey ? keyReady : stateReady)) return;
    if (fromKey) { key = value; keyError = error; keyReady = true; }
    else { state = value; stateError = error; stateReady = true; }
    if (stateReady && keyReady) {
      finished = true;
      // Finish both reads so a later state 401 still reaches the account cleanup path.
      if (stateError != null) result.done(null, null, stateError, false);
      else if (keyError != null) result.done(null, null, keyError, true);
      else result.done(state, key, null, false);
    }
  }
}
