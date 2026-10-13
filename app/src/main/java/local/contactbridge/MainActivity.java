package local.contactbridge;

import android.Manifest;
import android.accounts.*;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import static local.contactbridge.Model.*;

public final class MainActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final int GREEN=0xff176b51,INK=0xff172b24,MUTED=0xff64756e;
    private LinearLayout body,actions;
    private Spinner leftSpinner,rightSpinner;
    private CheckBox twoWay;
    private TextView status,previewText;
    private Switch autoSwitch;
    private List<Config.Ref> left=new ArrayList<>(),right=new ArrayList<>();
    private Config.Ref chosenGoogle;
    private Engine.Preview preview;
    private boolean busy;
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE);getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}
    @Override protected void onResume(){super.onResume();BridgeJob.schedule(this);loadAccounts();}
    private boolean permitted(){return checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.WRITE_CONTACTS)==PackageManager.PERMISSION_GRANTED;}
    private void loadAccounts() {
        if(busy)return;
        if(!permitted()){draw(Collections.emptyList());return;}
        busy=true;
        worker.execute(()->{
            try{List<Config.Ref> refs=new Repository(this).accounts();if(chosenGoogle!=null&&!refs.contains(chosenGoogle))refs.add(chosenGoogle);
                runOnUiThread(()->{busy=false;if(!isFinishing())draw(refs);});}
            catch(Exception e){runOnUiThread(()->{busy=false;draw(Collections.emptyList());status.setText("读取账号失败："+Engine.friendly(e));});}
        });
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,int color) {TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(3),1);return v;}
    private void gap(LinearLayout layout,int n){Space v=new Space(this);layout.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
    private LinearLayout card(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(18),dp(18),dp(18),dp(18));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(18));v.setBackground(bg);body.addView(v,new LinearLayout.LayoutParams(-1,-2));gap(body,16);return v;}
    private Button button(LinearLayout parent,String label,Runnable click){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(GREEN);parent.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->click.run());return b;}
    private void draw(List<Config.Ref> refs) {
        preview=null;
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xfff1f5f2);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(22),dp(24),dp(22),dp(24));scroll.addView(body);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{body.setPadding(dp(22),dp(24)+insets.getSystemWindowInsetTop(),dp(22),dp(24)+insets.getSystemWindowInsetBottom());return insets;});
        setContentView(scroll);scroll.requestApplyInsets();
        TextView title=text("通讯录桥",30,INK);title.setTypeface(null,Typeface.BOLD);body.addView(title);gap(body,6);
        body.addView(text("小米账号 ↔ Google ↔ iPhone",16,GREEN));gap(body,22);
        LinearLayout intro=card();intro.addView(text("在两边保留同一份联系人",19,INK));gap(intro,8);
        intro.addView(text("同步新增和修改。双方内容不同会提示处理；删除暂不自动传播。iPhone 使用同一 Google 账号开启通讯录即可。",14,MUTED));
        gap(intro,8);intro.addView(text("小米通讯录可能隐藏无号码联系人，本应用也会同步这些记录。联系人总数包含有号码和无号码两类；系统合并显示的一人可能对应多条账号记录。",12,MUTED));
        if(!permitted()) {
            LinearLayout permission=card();permission.addView(text("允许访问通讯录",19,INK));gap(permission,8);
            permission.addView(text("账号选择和同步需要读取、修改系统联系人。应用不会自行上传通讯录；云同步由手机原有账号服务完成。",14,MUTED));
            button(permission,"授权通讯录",()->requestPermissions(new String[]{Manifest.permission.READ_CONTACTS,Manifest.permission.WRITE_CONTACTS},10));
            status=text("等待通讯录权限",14,MUTED);body.addView(status);return;
        }
        left=new ArrayList<>();right=new ArrayList<>();for(Config.Ref r:refs){if(r.xiaomi())left.add(r);if(r.google())right.add(r);}
        Config saved=Config.load(this);
        LinearLayout accountCard=card();accountCard.addView(text("同步账号",19,INK));gap(accountCard,12);
        accountCard.addView(text("小米账号",13,MUTED));leftSpinner=spinner(accountCard,left,saved==null?null:saved.left);
        gap(accountCard,10);accountCard.addView(text("Google 账号",13,MUTED));rightSpinner=spinner(accountCard,right,saved==null?null:saved.right);
        button(accountCard,"选择 Google 账号",()->{
            Intent intent=AccountManager.newChooseAccountIntent(null,null,new String[]{"com.google"},"选择用于同步通讯录的 Google 账号",null,null,null);
            startActivityForResult(intent,20);
        });
        if(left.isEmpty())accountCard.addView(text("未发现小米账号联系人。请确认小米云通讯录已开启，并刷新账号。",13,0xffa05b11));
        button(accountCard,"刷新账号",this::loadAccounts);
        twoWay=new CheckBox(this);twoWay.setText("双向同步（含 iPhone 的新增和修改）");twoWay.setChecked(saved==null||saved.twoWay);accountCard.addView(twoWay);
        LinearLayout syncCard=card();syncCard.addView(text("先预览，再同步",19,INK));gap(syncCard,8);
        previewText=text("首次使用先让小米与 Google 云同步完成，再预览待同步的联系人。",14,MUTED);syncCard.addView(previewText);
        button(syncCard,"预览同步",this::makePreview);
        button(syncCard,"同步预览中的联系人",()->{
            if(preview==null){toast("请先预览同步");return;}
            Engine.Preview reviewed=preview;
            new AlertDialog.Builder(this).setTitle("确认本次同步").setMessage(reviewed.summary()+"\n\n将同步 "+reviewed.writes()+" 条账号记录。冲突和缺失联系人会保留，供你处理。")
                .setPositiveButton("同步",(d,w)->runTask(()->new Engine(this).apply(reviewed,()->false,false),true)).setNegativeButton("取消",null).show();
        });
        autoSwitch=new Switch(this);autoSwitch.setText("自动同步");autoSwitch.setPadding(0,dp(12),0,dp(12));autoSwitch.setChecked(Config.prefs(this).getBoolean("automatic",false));syncCard.addView(autoSwitch);
        autoSwitch.setOnCheckedChangeListener((v,checked)->{
            if(checked) {
                try{saveSelection();if(!Config.prefs(this).getBoolean("initialDone",false)){autoSwitch.setChecked(false);toast("请先预览并完成一次手动同步");return;}}
                catch(Exception e){autoSwitch.setChecked(false);toast(Engine.friendly(e));return;}
            }
            Config.prefs(this).edit().putBoolean("automatic",checked).commit();BridgeJob.schedule(this);
            toast(checked?"自动同步已开启":"自动同步已暂停");
        });
        syncCard.addView(text("联系人变化会触发后台同步，并约每 15 分钟检查一次。没有变化时只检查版本信息。时间由系统调度；云端变化需先下载到手机。",12,MUTED));
        button(syncCard,"打开应用设置",()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName()))));
        syncCard.addView(text("在小米应用设置中允许后台自启动，并将电池策略设为不限制。",12,MUTED));
        LinearLayout result=card();result.addView(text("运行状态",19,INK));gap(result,8);
        String when=Config.prefs(this).getString("lastRun","");status=text((when.isEmpty()?"":when+"\n")+Config.prefs(this).getString("status","尚未同步"),14,MUTED);result.addView(status);
        String checked=Config.prefs(this).getString("lastCheck","");if(!checked.isEmpty())result.addView(text("最近自动检查 "+checked+" · "+Config.prefs(this).getLong("checkMillis",0)+" 毫秒",12,MUTED));
        gap(result,12);actions=new LinearLayout(this);actions.setOrientation(LinearLayout.VERTICAL);result.addView(actions);
        body.addView(text("v0.1.3 · 仅桥接选中的两个账号 · 不同步通话记录",12,MUTED));
    }
    private Spinner spinner(LinearLayout card,List<Config.Ref> refs,Config.Ref selected) {
        Spinner s=new Spinner(this);List<String> labels=new ArrayList<>();for(Config.Ref r:refs)labels.add(r.toString());if(labels.isEmpty())labels.add("未发现账号");
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(adapter);card.addView(s,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView counts=text("",12,MUTED);card.addView(counts);
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){counts.setText("");}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){if(position<refs.size()){Config.Ref ref=refs.get(position);counts.setText(ref.withPhone+" 个有号码 · "+(ref.count-ref.withPhone)+" 个无号码");}}});
        if(selected!=null&&refs.contains(selected))s.setSelection(refs.indexOf(selected));return s;
    }
    private void saveSelection() {
        if(left.isEmpty()||right.isEmpty())throw new IllegalStateException("请选择小米账号和 Google 账号");
        Config.save(this,new Config(left.get(leftSpinner.getSelectedItemPosition()),right.get(rightSpinner.getSelectedItemPosition()),twoWay.isChecked()));
    }
    private void makePreview(){if(busy)return;try{saveSelection();}catch(Exception e){toast(Engine.friendly(e));return;}runTask(()->{preview=new Engine(this).preview();return preview.summary();},false);}
    private interface Task{String run()throws Exception;}
    private void runTask(Task task,boolean refresh) {
        if(busy){toast("正在处理，请稍候");return;}
        busy=true;status.setText("正在处理…");
        worker.execute(()->{
            String result;try{result=task.run();}catch(Exception e){result="处理未完成："+Engine.friendly(e);}String message=result;
            runOnUiThread(()->{busy=false;if(isFinishing())return;status.setText(message);if(refresh){preview=null;previewText.setText("请重新预览当前通讯录；运行状态中可查看待处理项。");actions.removeAllViews();}else if(preview!=null){previewText.setText(preview.summary());showActions();}});
        });
    }
    private void showActions() {
        actions.removeAllViews();int shown=0;
        for(Action a:preview.actions){
            if(a.kind==Kind.LINK||a.kind==Kind.BASELINE)continue;
            if(shown++>=80){actions.addView(text("仅展示前 80 项，统计包含全部联系人。",12,MUTED));break;}
            String direction=a.kind==Kind.CREATE_RIGHT||a.kind==Kind.UPDATE_RIGHT?" → Google":a.kind==Kind.CREATE_LEFT||a.kind==Kind.UPDATE_LEFT?" → 小米":"";
            String label=a.kind==Kind.CONFLICT?"内容冲突":a.kind==Kind.AMBIGUOUS?"配对不明确":a.kind==Kind.MISSING?"缺失或身份变化，暂停配对":a.kind==Kind.EMPTY?"没有可同步内容":"待同步";
            actions.addView(text(a.label()+" · "+label+direction,14,a.writes()?INK:0xffa05b11));
            if(a.kind==Kind.CONFLICT&&a.left!=null&&a.right!=null)button(actions,"处理这项冲突",()->showConflict(a));gap(actions,8);
        }
    }
    private String details(Contact c){
        StringBuilder b=new StringBuilder(c.label());
        for(Row row:c.rows){
            if(PHOTO.equals(row.mime)){b.append("\n照片：有（请在系统通讯录查看照片）");continue;}
            String label=NAME.equals(row.mime)?"姓名":PHONE.equals(row.mime)?"电话":EMAIL.equals(row.mime)?"邮箱":row.mime.endsWith("nickname")?"昵称":row.mime.endsWith("organization")?"单位":row.mime.endsWith("note")?"备注":row.mime.endsWith("postal-address_v2")?"地址":row.mime.endsWith("website")?"网站":"日期";
            List<String> values=new ArrayList<>();
            for(int i=0;i<15;i++)if((NAME.equals(row.mime)||i!=1&&i!=2)&&!row.value(i).isEmpty()&&!values.contains(row.value(i)))values.add(row.value(i));
            if(!values.isEmpty())b.append("\n").append(label).append("：").append(String.join(" · ",values));
        }
        return b.toString();
    }
    private void showConflict(Action a) {
        Engine.Preview reviewed=preview;
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("选择保留哪一边").setMessage("小米\n"+details(a.left)+"\n\nGoogle\n"+details(a.right)+"\n\n所选一方的姓名、电话、邮箱、照片、昵称、单位、备注、地址、网站和日期将覆盖另一方；另一方独有的这些内容也会移除。")
            .setPositiveButton("以小米为准",(d,w)->runTask(()->{new Engine(this).resolve(reviewed,a,true);return "冲突已处理，请重新预览";},true))
            .setNeutralButton("取消",null);
        if(reviewed.config.twoWay)dialog.setNegativeButton("以 Google 为准",(d,w)->runTask(()->{new Engine(this).resolve(reviewed,a,false);return "冲突已处理，请重新预览";},true));
        dialog.show();
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);loadAccounts();}
    @Override protected void onActivityResult(int r,int code,Intent data){super.onActivityResult(r,code,data);if(r==20&&code==RESULT_OK&&data!=null){String name=data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME),type=data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);if(name!=null&&"com.google".equals(type))chosenGoogle=new Config.Ref(name,type);loadAccounts();}}
    @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
}
