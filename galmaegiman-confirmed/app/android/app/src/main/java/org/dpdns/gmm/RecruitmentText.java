package org.dpdns.gmm;

/** Native presentation vocabulary only. Never changes API paths, JSON values or server state. */
final class RecruitmentText {
  private RecruitmentText() {}

  static String display(String text) {
    return text == null ? "" : text
        .replace("뽑기권을", "지원서를").replace("뽑기권은", "지원서는")
        .replace("뽑기권이", "지원서가").replace("뽑기권과", "지원서와")
        .replace("뽑기권으로", "지원서로").replace("뽑기권", "지원서")
        .replace("뽑기를", "모집을").replace("뽑기는", "모집은")
        .replace("뽑기가", "모집이").replace("뽑기로", "모집으로")
        .replace("뽑기", "모집");
  }
}
