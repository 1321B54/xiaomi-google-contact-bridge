package local.contactbridge;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static local.contactbridge.Model.*;

final class Store {
    private final AtomicFile file;
    private final String key;
    Store(Context c,Config cfg) throws Exception {
        key=cfg.key();StringBuilder name=new StringBuilder("pairs-");
        for(byte b:MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)))name.append(String.format(Locale.ROOT,"%02x",b&255));
        file=new AtomicFile(new File(c.getFilesDir(),name+".json"));
    }
    List<Pair> load() throws Exception {
        if(!file.getBaseFile().exists()&&!new File(file.getBaseFile()+".bak").exists())return new ArrayList<>();
        JSONObject state=new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8));
        if(!key.equals(state.getString("accountPair")))throw new IOException("配对状态与当前账号不匹配");
        List<Pair> pairs=new ArrayList<>();JSONArray array=state.getJSONArray("pairs");
        Set<Long> left=new HashSet<>(),right=new HashSet<>();
        for(int i=0;i<array.length();i++){
            JSONObject p=array.getJSONObject(i);long l=p.getLong("left"),r=p.getLong("right");
            if(!left.add(l)||!right.add(r))throw new IOException("联系人配对状态重复，请停止同步并检查");
            Pair pair=new Pair(l,r,p.getString("leftHash"),p.getString("rightHash"),nullable(p,"leftSource"),nullable(p,"rightSource"));pair.pending=p.optBoolean("pending",false);pairs.add(pair);
        }
        return pairs;
    }
    private String nullable(JSONObject p,String key)throws JSONException{return p.isNull(key)?null:p.getString(key);}
    void save(List<Pair> pairs)throws Exception {
        JSONObject state=new JSONObject().put("accountPair",key);JSONArray array=new JSONArray();
        for(Pair p:pairs)array.put(new JSONObject().put("left",p.left).put("right",p.right).put("leftHash",p.leftHash).put("rightHash",p.rightHash)
            .put("leftSource",p.leftSource==null?JSONObject.NULL:p.leftSource).put("rightSource",p.rightSource==null?JSONObject.NULL:p.rightSource).put("pending",p.pending));
        state.put("pairs",array);FileOutputStream output=null;
        try{output=file.startWrite();output.write(state.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(output);}
        catch(Exception e){if(output!=null)file.failWrite(output);throw e;}
    }
    static Pair baseline(Contact left,Contact right){return new Pair(left.id,right.id,left.hash(),right.hash(),left.sourceId,right.sourceId);}
    static void put(List<Pair> pairs,Pair p){pairs.removeIf(old->old.left==p.left||old.right==p.right);pairs.add(p);}
}
