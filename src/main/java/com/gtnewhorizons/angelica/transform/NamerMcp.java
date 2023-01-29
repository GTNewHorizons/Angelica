package com.gtnewhorizons.angelica.transform;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.gtnewhorizons.angelica.transform.Names.Clas;
import com.gtnewhorizons.angelica.transform.Names.Fiel;
import com.gtnewhorizons.angelica.transform.Names.Meth;

public class NamerMcp extends NamerSrg {

    public void setNames() {
        setNamesSrg();
        lookupReobfName("../conf/");
        rename("../conf/");
    }

    public void rename(String confPath) {
        Map<String, String> nameMap;
        nameMap = loadNameMapCSV(confPath + "fields.csv");
        for (Fiel f : af) {
            String s = nameMap.get(f.name);
            if (s != null) {
                f.name = s;
            }
        }
        nameMap = loadNameMapCSV(confPath + "methods.csv");
        for (Meth m : am) {
            String s = nameMap.get(m.name);
            if (s != null) {
                m.name = s;
            }
        }
    }

    Map loadNameMapCSV(String fileName) {
        Map map = new HashMap();
        BufferedReader rd = null;
        try {
            rd = new BufferedReader(new FileReader(fileName));
            String line;
            rd.readLine(); // skip first line;
            while ((line = rd.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens.length > 1) {
                    map.put(tokens[0], tokens[1]);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (rd != null) {
                try {
                    rd.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return map;
    }

    void lookupReobfName(String confPath) {
        Map<String, String> nameMap;
        nameMap = loadReobfMap(confPath + "joined.srg");
        for (Clas c : ac) {
            String s = nameMap.get(c.name);
            System.out.printf("C %s %s\n", c.name, s);
        }
        for (Fiel f : af) {
            String s = nameMap.get(f.clas + "/" + f.name);
            System.out.printf("F %s %s\n", f.name, s);
        }
        for (Meth m : am) {
            String s = nameMap.get(m.clas + "/" + m.name + m.desc);
            System.out.printf("M %s %s\n", m.name, s);
        }
    }

    Map loadReobfMap(String fileName) {
        Map map = new HashMap();
        BufferedReader rd = null;
        try {
            rd = new BufferedReader(new FileReader(fileName));
            String line;
            while ((line = rd.readLine()) != null) {
                String[] tokens = line.split(" ");
                if (tokens.length > 1) {
                    if ("CL:".equals(tokens[0])) {
                        map.put(tokens[2], tokens[1]);
                    } else if ("FD:".equals(tokens[0])) {
                        map.put(tokens[2], tokens[1]);
                    } else if ("MD:".equals(tokens[0])) {
                        map.put(tokens[3] + tokens[4], tokens[1].substring(tokens[1].lastIndexOf('/') + 1));
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (rd != null) {
                try {
                    rd.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return map;
    }
}
