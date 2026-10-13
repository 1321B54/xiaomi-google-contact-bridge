package local.contactbridge;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.BooleanSupplier;
import static local.contactbridge.Model.*;

final class Engine {
    static final Object LOCK=new Object();
    static final class Preview {
        final Config config;
        final Map<Long,Repository.Entry> left,right;
        final List<Action> actions;
        final List<Pair> pairs;
        Preview(Config c,Map<Long,Repository.Entry> l,Map<Long,Repository.Entry> r,List<Action> a,List<Pair> p){config=c;left=l;right=r;actions=a;pairs=p;}
        int count(Kind...kinds){int n=0;for(Action a:actions)for(Kind k:kinds)if(a.kind==k)n++;return n;}
        int writes(){return count(Kind.CREATE_LEFT,Kind.CREATE_RIGHT,Kind.UPDATE_LEFT,Kind.UPDATE_RIGHT);}
        private Counts accountCounts(Map<Long,Repository.Entry> entries){List<Contact> contacts=new ArrayList<>();for(Repository.Entry e:entries.values())contacts.add(e.contact);return Model.counts(contacts);}
        String summary(){return "小米："+accountCounts(left).description()+"\nGoogle："+accountCounts(right).description()+"\n待处理账号记录：新增 "+count(Kind.CREATE_LEFT,Kind.CREATE_RIGHT)+" · 更新 "+count(Kind.UPDATE_LEFT,Kind.UPDATE_RIGHT)+" · 冲突 "+count(Kind.CONFLICT,Kind.AMBIGUOUS)+" · 缺失 "+count(Kind.MISSING);}
    }
    private final Context context;
    private final Repository repository;
    private boolean completedWithoutErrors;
    Engine(Context c){context=c.getApplicationContext();repository=new Repository(context);}
    void automatic(BooleanSupplier cancelled)throws Exception {
        synchronized(LOCK) {
            Config cfg=Config.load(context);if(cfg==null||cancelled.getAsBoolean()||!Config.prefs(context).getBoolean("automatic",false))return;
            long start=android.os.SystemClock.elapsedRealtime();
            String revision=cfg.key()+":"+cfg.twoWay+":"+repository.revision(cfg.left)+":"+repository.revision(cfg.right);
            if(!revision.equals(Config.prefs(context).getString("lastRevision",""))) {
                apply(preview(),cancelled,true);
                if(completedWithoutErrors&&!cancelled.getAsBoolean()&&Config.prefs(context).getBoolean("automatic",false))Config.prefs(context).edit().putString("lastRevision",revision).apply();
            }
            Config.prefs(context).edit().putString("lastCheck",new SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date()))
                .putLong("checkMillis",android.os.SystemClock.elapsedRealtime()-start).apply();
        }
    }
    Preview preview()throws Exception {
        synchronized(LOCK) {
            Config cfg=Config.load(context);if(cfg==null)throw new IllegalStateException("请先保存两个账号");
            List<Config.Ref> accounts=repository.accounts();
            if(!accounts.contains(cfg.left)||!accounts.contains(cfg.right))throw new IllegalStateException("选中的账号不可用，请重新选择账号");
            Map<Long,Repository.Entry> left=repository.read(cfg.left),right=repository.read(cfg.right);
            List<Pair> pairs=new Store(context,cfg).load();
            List<Contact> l=new ArrayList<>(),r=new ArrayList<>();
            for(Repository.Entry e:left.values())l.add(e.contact);for(Repository.Entry e:right.values())r.add(e.contact);
            return new Preview(cfg,left,right,Planner.plan(l,r,pairs,cfg.twoWay),pairs);
        }
    }
    String apply(Preview preview,BooleanSupplier cancelled,boolean automatic)throws Exception {
        synchronized(LOCK) {
            checkConfig(preview.config);Store store=new Store(context,preview.config);
            List<Pair> pairs=store.load();int applied=0,skipped=0;List<String> errors=new ArrayList<>();completedWithoutErrors=true;
            List<Action> actions=preview.actions;
            for(int index=0;index<actions.size();index++) {
                Action a=actions.get(index);
                if(cancelled.getAsBoolean()||(automatic&&!Config.prefs(context).getBoolean("automatic",false))){completedWithoutErrors=false;break;}
                checkConfig(preview.config);
                try {
                    if(a.kind==Kind.LINK||a.kind==Kind.BASELINE) {
                        Repository.Entry l=repository.one(preview.config.left,a.left.id),r=repository.one(preview.config.right,a.right.id);
                        if(l==null||r==null||!l.contact.hash().equals(a.left.hash())||!r.contact.hash().equals(a.right.hash()))throw new IllegalStateException("预览后联系人发生变化，请刷新");
                        Store.put(pairs,Store.baseline(l.contact,r.contact));store.save(pairs);
                    } else if(a.writes()) {
                        boolean toRight=a.kind==Kind.CREATE_RIGHT||a.kind==Kind.UPDATE_RIGHT;
                        copy(preview,a,toRight,pairs,store);applied++;
                    } else skipped++;
                }catch(Exception ex){completedWithoutErrors=false;skipped++;if(errors.size()<5)errors.add("第 "+(index+1)+" 项："+friendly(ex));}
            }
            if(!automatic&&!cancelled.getAsBoolean())Config.prefs(context).edit().putBoolean("initialDone",true).remove("lastRevision").apply();
            if(applied>0)repository.requestCloud(preview.config);
            String result="已同步 "+applied+" 条账号记录，待处理 "+skipped+" 项"+(errors.isEmpty()?"":"\n"+String.join("\n",errors));
            status(result);return result;
        }
    }
    void resolve(Preview preview,Action conflict,boolean useLeft)throws Exception {
        synchronized(LOCK){
            checkConfig(preview.config);
            if(!preview.actions.contains(conflict)||conflict.kind!=Kind.CONFLICT||conflict.left==null||conflict.right==null)throw new IllegalArgumentException("请重新预览有效的双侧冲突");
            if(!useLeft&&!preview.config.twoWay)throw new IllegalStateException("单向模式只能以小米为准");
            Store store=new Store(context,preview.config);List<Pair> pairs=store.load();copy(preview,conflict,useLeft,pairs,store);Config.prefs(context).edit().remove("lastRevision").apply();repository.requestCloud(preview.config);status("已处理 1 项冲突");
        }
    }
    private void copy(Preview p,Action a,boolean toRight,List<Pair> pairs,Store store)throws Exception {
        Repository.Entry l=a.left==null?null:p.left.get(a.left.id),r=a.right==null?null:p.right.get(a.right.id);
        Repository.Entry source=toRight?l:r,target=toRight?r:l;
        long id=repository.copy(toRight?p.config.left:p.config.right,source,toRight?p.config.right:p.config.left,target);
        // Retain the source snapshot's hash: an edit after the transaction must remain detectable.
        Repository.Entry newLeft=repository.one(p.config.left,toRight?l.contact.id:id);
        Repository.Entry newRight=repository.one(p.config.right,toRight?id:r.contact.id);
        if(newLeft==null||newRight==null)throw new IllegalStateException("写入后联系人暂不可见，请刷新检查");
        Store.put(pairs,Model.afterCopy(source.contact,newLeft.contact,newRight.contact,toRight));store.save(pairs);
    }
    private void checkConfig(Config expected){Config current=Config.load(context);if(current==null||!current.key().equals(expected.key())||current.twoWay!=expected.twoWay)throw new IllegalStateException("账号设置已变化，请重新预览");}
    void status(String text){Config.prefs(context).edit().putString("status",text).putString("lastRun",new SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date())).apply();}
    // Only these fixed, reviewed messages may ever leave Engine. Provider exception text,
    // SQL literals and account identifiers are replaced by a generic retry hint.
    private static final Set<String> SAFE_MESSAGES=Set.of(
        "请选择小米账号和 Google 账号",
        "保存设置失败",
        "请先保存两个账号",
        "选中的账号不可用，请重新选择账号",
        "预览后联系人发生变化，请刷新",
        "请重新预览有效的双侧冲突",
        "单向模式只能以小米为准",
        "写入后联系人暂不可见，请刷新检查",
        "账号设置已变化，请重新预览",
        "系统通讯录暂时无法读取，请稍后重试",
        "目标联系人只读",
        "来源联系人没有可同步内容",
        "联系人内容过大，已跳过以保留原数据",
        "配对状态与当前账号不匹配",
        "联系人配对状态重复，请停止同步并检查");
    static String friendly(Exception e){String message=e.getMessage();return message!=null&&SAFE_MESSAGES.contains(message)?message:"操作未完成，请重试";}
}
