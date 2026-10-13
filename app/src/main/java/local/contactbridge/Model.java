package local.contactbridge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Platform independent contact model. Never stores cloud passwords. */
public final class Model {
    public static final String NAME = "vnd.android.cursor.item/name";
    public static final String PHONE = "vnd.android.cursor.item/phone_v2";
    public static final String EMAIL = "vnd.android.cursor.item/email_v2";
    public static final String PHOTO = "vnd.android.cursor.item/photo";
    public static final Set<String> SUPPORTED = Set.of(NAME, PHONE, EMAIL, PHOTO,
        "vnd.android.cursor.item/nickname", "vnd.android.cursor.item/organization",
        "vnd.android.cursor.item/note", "vnd.android.cursor.item/postal-address_v2",
        "vnd.android.cursor.item/website", "vnd.android.cursor.item/contact_event");

    public static final class Row {
        public final String mime;
        public final String[] data;
        public Row(String mime, String[] data) { this.mime = mime; this.data = Arrays.copyOf(data, 15); }
        public String value(int index) { return data[index] == null ? "" : data[index].trim(); }
        public String canonical() {
            String[] values = Arrays.copyOf(data, 15);
            for (int i=0;i<15;i++) values[i] = values[i] == null ? "" : values[i].trim();
            if (NAME.equals(mime) && Arrays.stream(values, 1, 9).anyMatch(v -> !v.isEmpty())) values[0] = "";
            if (PHONE.equals(mime)) { String normalized=phone(values[0]); if(!normalized.isEmpty())values[0]=normalized; values[3] = ""; }
            if (EMAIL.equals(mime)) values[0] = values[0].toLowerCase(Locale.ROOT);
            // Providers generate auxiliary photo-file metadata independently.
            if (PHOTO.equals(mime)) Arrays.fill(values, 0, 14, "");
            StringBuilder b = new StringBuilder(mime);
            for (String s : values) b.append('|').append(s.length()).append(':').append(s);
            return b.toString();
        }
    }
    public static final class Contact {
        public final long id;
        public final long aggregateId;
        public final String sourceId;
        public final List<Row> rows;
        public Contact(long id, String sourceId, List<Row> rows) {
            this(id,id,sourceId,rows);
        }
        public Contact(long id,long aggregateId,String sourceId,List<Row> rows) {
            this.aggregateId=aggregateId;
            this.id=id; this.sourceId=sourceId; this.rows=Collections.unmodifiableList(new ArrayList<>(rows));
        }
        public boolean hasPhone(){for(Row row:rows)if(PHONE.equals(row.mime)&&!row.value(0).isEmpty())return true;return false;}
        public String label() {
            for (Row row: rows) if (NAME.equals(row.mime)) {
                if (!row.value(0).isEmpty()) return row.value(0);
                return (row.value(2)+" "+row.value(1)).trim();
            }
            for (Row row: rows) if (PHONE.equals(row.mime)) return row.value(0);
            return "未命名联系人";
        }
        public String hash() {
            try {
                List<String> rowsSorted = new ArrayList<>();
                for (Row row: rows) rowsSorted.add(row.canonical());
                Collections.sort(rowsSorted);
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(String.join("\n", rowsSorted).getBytes(StandardCharsets.UTF_8));
                StringBuilder b = new StringBuilder();
                for (byte v: digest) b.append(String.format(Locale.ROOT,"%02x", v & 255));
                return b.toString();
            } catch (Exception ex) { throw new IllegalStateException(ex); }
        }
        public Set<String> identity() {
            Set<String> keys = new HashSet<>();
            for (Row r:rows) {
                if (PHONE.equals(r.mime)) { String p=phone(r.value(0)); if (!p.isEmpty()) keys.add("p:"+p); }
                if (EMAIL.equals(r.mime) && !r.value(0).isEmpty()) keys.add("e:"+r.value(0).toLowerCase(Locale.ROOT));
            }
            return keys;
        }
        public boolean hasData() {
            for(Row r:rows) {
                if(NAME.equals(r.mime)){for(int i=0;i<9;i++)if(!r.value(i).isEmpty())return true;}
                else if(PHONE.equals(r.mime)||EMAIL.equals(r.mime)){if(!r.value(0).isEmpty())return true;}
                else if(PHOTO.equals(r.mime)){if(!r.value(14).isEmpty())return true;}
                else for(int i=0;i<15;i++)if(i!=1&&i!=2&&!r.value(i).isEmpty())return true;
            }
            return false;
        }
    }
    public static final class Counts {
        public final int total,withPhone,withoutPhone;
        Counts(int total,int withPhone){this.total=total;this.withPhone=withPhone;this.withoutPhone=total-withPhone;}
        public String description(){return total+" 个联系人（"+withPhone+" 个有号码，"+withoutPhone+" 个无号码）";}
    }
    public static Counts counts(Collection<Contact> contacts){
        Set<Long> all=new HashSet<>(),phones=new HashSet<>();
        for(Contact c:contacts){all.add(c.aggregateId);if(c.hasPhone())phones.add(c.aggregateId);}
        return new Counts(all.size(),phones.size());
    }
    public static String phone(String v) {
        if(v==null||!v.trim().matches("\\+?[0-9()\\s.-]+"))return "";
        String digits = v.replaceAll("[^0-9]", "");
        if(digits.isEmpty())return "";
        // Normalize explicit Chinese international prefixes only, not arbitrary numbers starting 86.
        if ((v.trim().startsWith("+86") || v.trim().startsWith("0086"))) {
            String prefix = v.trim().startsWith("0086") ? "0086" : "86";
            String rest = digits.substring(prefix.length());
            if (rest.matches("1[0-9]{10}")) return rest;
        }
        if (v.trim().startsWith("+")) return "+"+digits;
        return digits;
    }
    public static boolean matches(Contact a, Contact b) {
        Set<String> keys = a.identity();
        for (String key : b.identity()) if (keys.contains(key)) return true;
        return false;
    }
    public static final class Pair {
        public long left, right;
        public String leftHash, rightHash, leftSource, rightSource;
        public boolean pending;
        public Pair(long l,long r,String lh,String rh,String ls,String rs) {
            left=l;right=r;leftHash=lh;rightHash=rh;leftSource=ls;rightSource=rs;
        }
    }
    public static Pair afterCopy(Contact expectedSource,Contact actualLeft,Contact actualRight,boolean toRight) {
        Pair pair=new Pair(actualLeft.id,actualRight.id,actualLeft.hash(),actualRight.hash(),actualLeft.sourceId,actualRight.sourceId);
        String expected=expectedSource.hash();
        if(toRight){pair.leftHash=expected;if(expectedSource.sourceId!=null)pair.leftSource=expectedSource.sourceId;pair.pending=!actualRight.hash().equals(expected);}
        else{pair.rightHash=expected;if(expectedSource.sourceId!=null)pair.rightSource=expectedSource.sourceId;pair.pending=!actualLeft.hash().equals(expected);}
        return pair;
    }
    public enum Kind { CREATE_RIGHT, CREATE_LEFT, UPDATE_RIGHT, UPDATE_LEFT, LINK, BASELINE, CONFLICT, MISSING, EMPTY, AMBIGUOUS }
    public static final class Action {
        public final Kind kind;
        public final Contact left, right;
        public final Pair pair;
        public Action(Kind k,Contact l,Contact r,Pair p) {kind=k;left=l;right=r;pair=p;}
        public String label() {return left!=null ? left.label() : right!=null ? right.label() : "已配对联系人";}
        public boolean writes() {return kind==Kind.CREATE_RIGHT||kind==Kind.CREATE_LEFT||kind==Kind.UPDATE_RIGHT||kind==Kind.UPDATE_LEFT;}
    }
}
