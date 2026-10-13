package local.contactbridge;

import android.accounts.Account;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.Objects;

final class Config {
    static final class Ref {
        final String name, type;
        int count,withPhone;
        Ref(String name, String type) { this.name=name; this.type=type; }
        String key() { return type.length()+":"+type+name; }
        Account account() { return new Account(name,type); }
        boolean google() { return "com.google".equals(type); }
        boolean xiaomi() { return type.toLowerCase(java.util.Locale.ROOT).contains("xiaomi") || type.toLowerCase(java.util.Locale.ROOT).contains("miui"); }
        @Override public boolean equals(Object o) { return o instanceof Ref && Objects.equals(name,((Ref)o).name)&&Objects.equals(type,((Ref)o).type); }
        @Override public int hashCode() { return Objects.hash(name,type); }
        @Override public String toString() { return (google()?"Google":xiaomi()?"小米":"其他")+" · "+name+"（"+count+" 人）"; }
    }
    final Ref left,right;
    final boolean twoWay;
    Config(Ref left,Ref right,boolean twoWay) {this.left=left;this.right=right;this.twoWay=twoWay;}
    String key() {return left.key()+"↔"+right.key();}
    static SharedPreferences prefs(Context c) {return c.getSharedPreferences("bridge",Context.MODE_PRIVATE);}
    static Config load(Context c) {
        SharedPreferences p=prefs(c);
        String ln=p.getString("leftName",null),rn=p.getString("rightName",null),lt=p.getString("leftType",null),rt=p.getString("rightType",null);
        if(ln==null||rn==null||lt==null||rt==null)return null;
        return new Config(new Ref(ln,lt),new Ref(rn,rt),p.getBoolean("twoWay",true));
    }
    static void save(Context c,Config config) {
        if(!config.left.xiaomi()||!config.right.google())throw new IllegalArgumentException("请选择小米账号和 Google 账号");
        Config old=load(c);
        boolean reset=old==null||!old.key().equals(config.key())||old.twoWay!=config.twoWay;
        SharedPreferences.Editor e=prefs(c).edit().putString("leftName",config.left.name).putString("leftType",config.left.type)
            .putString("rightName",config.right.name).putString("rightType",config.right.type).putBoolean("twoWay",config.twoWay);
        if(reset)e.putBoolean("automatic",false).putBoolean("initialDone",false).remove("lastRevision");
        if(!e.commit())throw new IllegalStateException("保存设置失败");
        if(reset)BridgeJob.schedule(c);
    }
}
