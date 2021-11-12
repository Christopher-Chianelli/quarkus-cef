package io.quarkiverse.cef;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;

import org.cef.CefClient;
import org.cef.browser.CefBrowser;

public class HTMLFrame extends JFrame {
    private final CefBrowser browser;

    public HTMLFrame(String url, CefClient client, CefClientActiveCondition cefClientActiveCondition) {
        browser = client.createBrowser(url, false, false);
        Component browerUI = browser.getUIComponent();
        getContentPane().add(browerUI, BorderLayout.CENTER);
        pack();
        setSize(800, 600);
        setVisible(true);
        cefClientActiveCondition.onCreate(this);
        HTMLFrame frame = this;
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                cefClientActiveCondition.onClose(frame);
            }
        });
    }

    public void setAddress(String address) {
        browser.loadURL(address);
    }
}
