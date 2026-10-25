package org.dpdns.gmm;
/** UI filtering only. The server still authorizes every encounter and restores active battles. */
enum Roster60BattleMode {
 ALL(""), STORY("story"), TRAINING("duel"), ADVENTURE("adventure");
 final String wire;
 Roster60BattleMode(String wire){this.wire=wire;}
 boolean includes(String value){return this==ALL||wire.equals(value);}
}
