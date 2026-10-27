package org.dpdns.gmm;

import org.json.JSONObject;

/** Single-use management-entry options read. No TTL, account snapshot or mutation cache. */
final class PartyEntryRead {
  final String owner;
  final int epoch;
  private boolean ready, consumed;
  private JSONObject data;
  private String error;
  private NativeApi.Result receiver;

  PartyEntryRead(NativeApi api, int epoch) {
    owner = api.token();
    this.epoch = epoch;
    api.call("native/roster60/pvp/options", null, this::complete);
  }

  String failure() { return ready ? error : null; }

  boolean matches(String account, int navigationEpoch) {
    return !consumed && owner.equals(account) && epoch == navigationEpoch;
  }

  void consume(NativeApi.Result receiver) {
    if (consumed) throw new IllegalStateException("Party entry read already consumed");
    consumed = true;
    this.receiver = receiver;
    deliver();
  }

  private void complete(JSONObject value, String failure) {
    if (ready) return;
    ready = true;
    data = value;
    error = failure;
    deliver();
  }

  private void deliver() {
    if (!ready || receiver == null) return;
    NativeApi.Result result = receiver;
    receiver = null;
    JSONObject value = data;
    data = null;
    result.done(value, error);
  }
}
