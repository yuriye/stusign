package com.ys;

import argo.jdom.JdomParser;
import argo.jdom.JsonNode;
import argo.jdom.JsonNodeType;
import argo.jdom.JsonStringNode;
import argo.saj.InvalidSyntaxException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SettingsReader {
    private static final Logger log = LoggerFactory.getLogger(SettingsReader.class);
    private static final Map<String, String> settings = new LinkedHashMap();
    private static SettingsReader instance;
    private static final JdomParser jdom = new JdomParser();

    public void loadLocalSettings() {
        try {
            JsonNode root = jdom.parse(new InputStreamReader(new FileInputStream("settings.json")));
            if (root.getType() != JsonNodeType.OBJECT) {
                throw new IOException("Invalid JSON settings object");
            }

            Iterator i$ = root.getFields().keySet().iterator();

            while(true) {
                JsonStringNode node;
                do {
                    if (!i$.hasNext()) {
                        return;
                    }

                    node = (JsonStringNode)i$.next();
                } while(root.getNode(new Object[]{node.getText()}).getType() != JsonNodeType.STRING);

                if (!"controlPanelUuid".equals(node.getText()) && !"thisDeviceUuid".equals(node.getText())) {
                    settings.put(node.getText(), ((JsonNode)root.getFields().get(node)).getText());
                } else {
                    settings.put("deviceUuid", ((JsonNode)root.getFields().get(node)).getText());
                }

                log.info("load local setting \"{}\" : \"{}\"", node.getText(), ((JsonNode)root.getFields().get(node)).getText());
            }
        } catch (IOException var4) {
            log.error(var4.getMessage(), var4);
        } catch (InvalidSyntaxException var5) {
            log.error(var5.getMessage(), var5);
        }

    }

    public SettingsReader() {
    }

    public static synchronized SettingsReader getInstance() {
        if (instance == null) {
            instance = new SettingsReader();
        }

        return instance;
    }

    public static Map<String, String> getSettings() {
        return settings;
    }

    public static String getParam(String param) {
        return (String)settings.get(param);
    }

    public static void putParam(String paramName, String param) {
        settings.put(paramName, param);
    }
}

