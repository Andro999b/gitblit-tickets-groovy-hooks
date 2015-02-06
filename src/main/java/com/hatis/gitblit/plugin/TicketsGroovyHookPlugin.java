package com.hatis.gitblit.plugin;

import com.gitblit.extensions.GitblitPlugin;
import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.Version;

/**
 *
 * @author andro
 */
public class TicketsGroovyHookPlugin extends GitblitPlugin {

    public TicketsGroovyHookPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        log.info("{} STARTED.", getWrapper().getPluginId());
    }

    @Override
    public void stop() {
        log.info("{} STOPPED.", getWrapper().getPluginId());
    }

    @Override
    public void onInstall() {
        log.info("{} INSTALLED.", getWrapper().getPluginId());
    }

    @Override
    public void onUpgrade(Version oldVersion) {
        log.info("{} UPGRADED from {}.", getWrapper().getPluginId(), oldVersion);
    }

    @Override
    public void onUninstall() {
        log.info("{} UNINSTALLED.", getWrapper().getPluginId());
    }

}
