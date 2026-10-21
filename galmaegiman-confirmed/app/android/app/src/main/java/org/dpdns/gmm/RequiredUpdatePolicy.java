package org.dpdns.gmm;

/** Android와 분리된 Play 확인 정책. 오류만으로 새 버전을 만들어 내지 않는다. */
final class RequiredUpdatePolicy {
  private final long installed;
  private long required;
  private boolean checking = true, inProgress;

  RequiredUpdatePolicy(long installed, long previouslyConfirmed) {
    this.installed = installed;
    required = previouslyConfirmed > installed ? previouslyConfirmed : 0;
  }
  void checking() { checking = true; }
  void available(long version) {
    checking = false;
    inProgress = false;
    required = version > installed ? Math.max(required, version) : 0;
  }
  void inProgress(long version) { available(version); inProgress = true; }
  void unavailable() { checking = false; required = 0; inProgress = false; }
  void failed() { checking = false; }
  boolean blocks() { return checking || required(); }
  boolean required() { return inProgress || required > installed; }
  boolean isChecking() { return checking; }
  long confirmedVersion() { return required; }
}
