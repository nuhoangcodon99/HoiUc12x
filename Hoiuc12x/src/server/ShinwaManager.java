package server;

import io.SQLManager;
import java.sql.SQLException;
import stream.Server;
import template.ItemTemplate;
import template.ShinwaTemplate;
import org.json.simple.JSONArray;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShinwaManager {

    public static HashMap<Integer, List<ShinwaTemplate>> entrys = new HashMap<>();

    public static synchronized void update(ShinwaTemplate item, int idList) {
        try {
            List<ShinwaTemplate> listItem = ShinwaManager.entrys.get(idList);
            if (listItem.remove(item)) {
                ShinwaManager.entrys.get(-1).add(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ShinwaTemplate findItem(int type, int id) {
        if (type < 0 || type >= ShinwaManager.entrys.size()) {
            return null;
        }
        List<ShinwaTemplate> list = ShinwaManager.entrys.get(type);
        if (list != null) {
            for (ShinwaTemplate item : list) {
                if (item.getId() == id) {
                    return item;
                }
            }
        } else {
            System.err.println("list is null");
        }
        return null;
    }

    public static void close() {
        ShinwaManager.flush();
        System.out.println("Flush/Close ShinwaManager");
    }

    public static synchronized void flush() {
        try {
            synchronized (Server.LOCK_MYSQL) {
                synchronized (ShinwaManager.entrys) {
                    for (Map.Entry<Integer, List<ShinwaTemplate>> entry : ShinwaManager.entrys.entrySet()) {
                        Integer key = entry.getKey();
                        List<ShinwaTemplate> list = entry.getValue();
                        JSONArray jarr = new JSONArray();
                        for (ShinwaTemplate item : list) {
                            JSONArray jarr2 = new JSONArray();
                            jarr2.add(ItemTemplate.ObjectItem(item.getItem()));
                            jarr2.add(item.getTimeStart());
                            jarr2.add(item.getSeller());
                            jarr2.add(item.getPrice());
                            jarr.add(jarr2);
                        }
                        String sqlSET = "`data`='" + jarr.toJSONString() + "'";
                        jarr.clear();
                        SQLManager.stat.executeUpdate("UPDATE `shinwa` SET " + sqlSET + " WHERE `id`=" + key + ";");
                    }
                }
            }
        } catch (SQLException var10) {
        }
    }
}
