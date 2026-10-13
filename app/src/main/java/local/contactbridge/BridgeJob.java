package local.contactbridge;

import android.Manifest;
import android.app.job.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.provider.ContactsContract;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BridgeJob extends JobService {
    private static final int PERIODIC=24101,CHANGED=24102;
    private final java.util.concurrent.ConcurrentHashMap<Integer,AtomicBoolean> running=new java.util.concurrent.ConcurrentHashMap<>();
    static void schedule(Context c) {
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);
        if(!Config.prefs(c).getBoolean("automatic",false)||c.checkSelfPermission(Manifest.permission.WRITE_CONTACTS)!=PackageManager.PERMISSION_GRANTED) {
            scheduler.cancel(PERIODIC);scheduler.cancel(CHANGED);return;
        }
        ComponentName component=new ComponentName(c,BridgeJob.class);
        if(scheduler.getPendingJob(PERIODIC)==null)scheduler.schedule(new JobInfo.Builder(PERIODIC,component).setPeriodic(15*60*1000L).setPersisted(true).build());
        if(scheduler.getPendingJob(CHANGED)==null)armChanges(c);
    }
    private static void armChanges(Context c) {
        if(!Config.prefs(c).getBoolean("automatic",false))return;
        c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(CHANGED,new ComponentName(c,BridgeJob.class))
            .addTriggerContentUri(new JobInfo.TriggerContentUri(ContactsContract.AUTHORITY_URI,JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
            .setTriggerContentUpdateDelay(5000).setTriggerContentMaxDelay(15000).build());
    }
    @Override public boolean onStartJob(JobParameters params) {
        AtomicBoolean stopped=new AtomicBoolean();running.put(params.getJobId(),stopped);
        new Thread(()->{
            try {
                if(Config.prefs(this).getBoolean("automatic",false)) {
                    new Engine(this).automatic(stopped::get);
                }
            }catch(Exception e){new Engine(this).status("自动同步待处理："+Engine.friendly(e));}
            finally{running.remove(params.getJobId(),stopped);if(!stopped.get()){if(params.getJobId()==CHANGED&&Config.prefs(this).getBoolean("automatic",false))armChanges(this);else jobFinished(params,false);}}
        },"contacts-bridge-job").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params){AtomicBoolean stopped=running.get(params.getJobId());if(stopped!=null)stopped.set(true);return stopped!=null&&params.getJobId()==CHANGED&&Config.prefs(this).getBoolean("automatic",false);}
}
