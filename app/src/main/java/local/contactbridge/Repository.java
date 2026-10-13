package local.contactbridge;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.*;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.Data;
import android.util.Base64;
import java.util.*;
import static local.contactbridge.Model.*;

final class Repository {
    static final class Entry {
        final Contact contact;
        final long version;
        final boolean readOnly;
        Entry(Contact c,long v,boolean ro){contact=c;version=v;readOnly=ro;}
    }
    private final ContentResolver resolver;
    private final Context context;
    Repository(Context c){context=c;resolver=c.getContentResolver();}

    List<Config.Ref> accounts() {
        Map<String,Config.Ref> refs=new LinkedHashMap<>();
        Map<Long,Config.Ref> owners=new HashMap<>();Map<Long,Long> aggregateIds=new HashMap<>();
        Map<String,Set<Long>> all=new HashMap<>(),withPhone=new HashMap<>();
        try(Cursor cur=resolver.query(RawContacts.CONTENT_URI,new String[]{RawContacts.ACCOUNT_NAME,RawContacts.ACCOUNT_TYPE,RawContacts._ID,RawContacts.CONTACT_ID},RawContacts.DELETED+"=0",null,null)) {
            requireCursor(cur);
            while(cur.moveToNext()) {
                if(cur.isNull(0)||cur.isNull(1))continue;
                Config.Ref r=new Config.Ref(cur.getString(0),cur.getString(1));
                if(!r.xiaomi()&&!r.google())continue;
                Config.Ref ref=refs.computeIfAbsent(r.key(),k->r);long rawId=cur.getLong(2),aggregate=cur.isNull(3)?rawId:cur.getLong(3);
                owners.put(rawId,ref);aggregateIds.put(rawId,aggregate);all.computeIfAbsent(ref.key(),k->new HashSet<>()).add(aggregate);
            }
        }
        try(Cursor cur=resolver.query(Data.CONTENT_URI,new String[]{Data.RAW_CONTACT_ID},Data.MIMETYPE+"=? AND "+Data.DATA1+" IS NOT NULL AND LENGTH(TRIM("+Data.DATA1+"))>0",new String[]{PHONE},null)){
            requireCursor(cur);while(cur.moveToNext()){long id=cur.getLong(0);Config.Ref ref=owners.get(id);if(ref!=null)withPhone.computeIfAbsent(ref.key(),k->new HashSet<>()).add(aggregateIds.get(id));}
        }
        for(Config.Ref ref:refs.values()){ref.count=all.getOrDefault(ref.key(),Set.of()).size();ref.withPhone=withPhone.getOrDefault(ref.key(),Set.of()).size();}
        try(Cursor cur=resolver.query(ContactsContract.Settings.CONTENT_URI,new String[]{RawContacts.ACCOUNT_NAME,RawContacts.ACCOUNT_TYPE},null,null,null)) {
            if(cur!=null)while(cur.moveToNext())if(!cur.isNull(0)&&!cur.isNull(1)){
                Config.Ref r=new Config.Ref(cur.getString(0),cur.getString(1)); refs.putIfAbsent(r.key(),r);
            }
        }
        for(Account a:AccountManager.get(context).getAccounts()) {
            Config.Ref r=new Config.Ref(a.name,a.type);refs.putIfAbsent(r.key(),r);
        }
        List<Config.Ref> out=new ArrayList<>();
        for(Config.Ref r:refs.values())if(r.xiaomi()||r.google())out.add(r);
        out.sort(Comparator.comparing(Config.Ref::key));return out;
    }

    Map<Long,Entry> read(Config.Ref ref) { return read(ref,null); }
    private Map<Long,Entry> read(Config.Ref ref,Long onlyId) {
        Map<Long,long[]> metadata=new LinkedHashMap<>();
        Map<Long,String> sourceIds=new HashMap<>();
        Map<Long,List<Row>> rows=new LinkedHashMap<>();
        String where=RawContacts.ACCOUNT_NAME+"=? AND "+RawContacts.ACCOUNT_TYPE+"=? AND "+RawContacts.DELETED+"=0";
        String[] accountArgs=new String[]{ref.name,ref.type};
        if(onlyId!=null){where+=" AND "+RawContacts._ID+"=?";accountArgs=new String[]{ref.name,ref.type,String.valueOf(onlyId)};}
        try(Cursor cur=resolver.query(RawContacts.CONTENT_URI,new String[]{RawContacts._ID,RawContacts.VERSION,RawContacts.SOURCE_ID,RawContacts.RAW_CONTACT_IS_READ_ONLY,RawContacts.CONTACT_ID},where,accountArgs,RawContacts._ID+" ASC")) {
            requireCursor(cur);
            while(cur.moveToNext()) {
                long id=cur.getLong(0);metadata.put(id,new long[]{cur.getLong(1),cur.getInt(3),cur.isNull(4)?id:cur.getLong(4)});
                sourceIds.put(id,cur.getString(2));rows.put(id,new ArrayList<>());
            }
        }
        List<Long> ids=new ArrayList<>(metadata.keySet());
        String[] projection=new String[17];projection[0]=Data.RAW_CONTACT_ID;projection[1]=Data.MIMETYPE;
        for(int i=0;i<15;i++)projection[i+2]="data"+(i+1);
        for(int start=0;start<ids.size();start+=150) {
            List<Long> chunk=ids.subList(start,Math.min(start+150,ids.size()));
            String[] args=chunk.stream().map(String::valueOf).toArray(String[]::new);
            String placeholders=String.join(",",Collections.nCopies(chunk.size(),"?"));
            try(Cursor cur=resolver.query(Data.CONTENT_URI,projection,Data.RAW_CONTACT_ID+" IN ("+placeholders+")",args,Data._ID+" ASC")) {
                requireCursor(cur);
                while(cur.moveToNext()) {
                    String mime=cur.getString(1);if(!SUPPORTED.contains(mime))continue;
                    String[] fields=new String[15];
                    for(int i=0;i<15;i++) {
                        if(cur.isNull(i+2))continue;
                        if(PHOTO.equals(mime)&&i==14)fields[i]=Base64.encodeToString(cur.getBlob(i+2),Base64.NO_WRAP);
                        else if(PHOTO.equals(mime))fields[i]=null;
                        else if(cur.getType(i+2)!=Cursor.FIELD_TYPE_BLOB)fields[i]=cur.getString(i+2);
                    }
                    if(PHOTO.equals(mime)&&(fields[14]==null||fields[14].isEmpty()))continue;
                    rows.get(cur.getLong(0)).add(new Row(mime,fields));
                }
            }
        }
        Map<Long,Entry> out=new LinkedHashMap<>();
        for(long id:ids)out.put(id,new Entry(new Contact(id,metadata.get(id)[2],sourceIds.get(id),rows.get(id)),metadata.get(id)[0],metadata.get(id)[1]!=0));
        return out;
    }
    Entry one(Config.Ref ref,long id) {return read(ref,id).get(id);}
    String revision(Config.Ref ref) {
        try {
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
            String where=RawContacts.ACCOUNT_NAME+"=? AND "+RawContacts.ACCOUNT_TYPE+"=? AND "+RawContacts.DELETED+"=0";
            try(Cursor c=resolver.query(RawContacts.CONTENT_URI,new String[]{RawContacts._ID,RawContacts.VERSION,RawContacts.SOURCE_ID},where,new String[]{ref.name,ref.type},RawContacts._ID+" ASC")) {
                requireCursor(c);while(c.moveToNext())digest.update((c.getLong(0)+":"+c.getLong(1)+":"+c.getString(2)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return Base64.encodeToString(digest.digest(),Base64.NO_WRAP);
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    static void requireCursor(Cursor c){if(c==null)throw new IllegalStateException("系统通讯录暂时无法读取，请稍后重试");}

    long copy(Config.Ref sourceRef,Entry source,Config.Ref targetRef,Entry target) throws Exception {
        if(target!=null&&target.readOnly)throw new IllegalStateException("目标联系人只读");
        if(source==null||!source.contact.hasData())throw new IllegalStateException("来源联系人没有可同步内容");
        ArrayList<ContentProviderOperation> ops=new ArrayList<>();
        ops.add(assertUnchanged(sourceRef,source));
        int insertIndex=-1;
        long targetId=-1;
        if(target==null) {
            insertIndex=ops.size();
            ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI).withValue(RawContacts.ACCOUNT_NAME,targetRef.name)
                .withValue(RawContacts.ACCOUNT_TYPE,targetRef.type).build());
        } else {
            targetId=target.contact.id;
            ops.add(assertUnchanged(targetRef,target));
            String supported=String.join(",",Collections.nCopies(SUPPORTED.size(),"?"));
            List<String> args=new ArrayList<>();args.add(String.valueOf(targetId));args.addAll(SUPPORTED);
            ops.add(ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection(Data.RAW_CONTACT_ID+"=? AND "+Data.MIMETYPE+" IN ("+supported+")",args.toArray(new String[0])).build());
        }
        int bytes=0;
        for(Row row:source.contact.rows) {
            if(!SUPPORTED.contains(row.mime))continue;
            ContentProviderOperation.Builder op=ContentProviderOperation.newInsert(Data.CONTENT_URI).withValue(Data.MIMETYPE,row.mime);
            if(target==null)op.withValueBackReference(Data.RAW_CONTACT_ID,insertIndex);
            else op.withValue(Data.RAW_CONTACT_ID,targetId);
            for(int i=0;i<15;i++)if(row.data[i]!=null) {
                if(PHOTO.equals(row.mime)&&i==14) {
                    byte[] blob=Base64.decode(row.data[i],Base64.NO_WRAP);bytes+=blob.length;op.withValue("data15",blob);
                } else if(!PHOTO.equals(row.mime)){bytes+=row.data[i].length()*2;op.withValue("data"+(i+1),row.data[i]);}
            }
            ops.add(op.build());
        }
        if(bytes>600000)throw new IllegalStateException("联系人内容过大，已跳过以保留原数据");
        ContentProviderResult[] result=resolver.applyBatch(ContactsContract.AUTHORITY,ops);
        if(target==null)targetId=ContentUris.parseId(result[insertIndex].uri);
        return targetId;
    }
    private ContentProviderOperation assertUnchanged(Config.Ref ref,Entry e) {
        return ContentProviderOperation.newAssertQuery(RawContacts.CONTENT_URI)
            .withSelection(RawContacts._ID+"=? AND "+RawContacts.VERSION+"=? AND "+RawContacts.ACCOUNT_NAME+"=? AND "+RawContacts.ACCOUNT_TYPE+"=? AND "+RawContacts.DELETED+"=0",
                new String[]{String.valueOf(e.contact.id),String.valueOf(e.version),ref.name,ref.type})
            .withExpectedCount(1).build();
    }
    void requestCloud(Config cfg) {
        for(Config.Ref ref:new Config.Ref[]{cfg.left,cfg.right})try {
            if(ContentResolver.getSyncAutomatically(ref.account(),ContactsContract.AUTHORITY))
                ContentResolver.requestSync(ref.account(),ContactsContract.AUTHORITY,new Bundle());
        } catch(Exception ignored) { /* The app can still bridge locally; the account adapter owns cloud sync. */ }
    }
}
