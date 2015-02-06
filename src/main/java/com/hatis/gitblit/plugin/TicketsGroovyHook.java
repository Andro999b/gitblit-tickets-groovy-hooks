/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.hatis.gitblit.plugin;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.extensions.PatchsetHook;
import com.gitblit.git.GitblitReceivePack;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.UserModel;
import com.gitblit.servlet.GitblitContext;
import com.gitblit.utils.ArrayUtils;
import groovy.lang.Binding;
import groovy.util.GroovyScriptEngine;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ro.fortsoft.pf4j.Extension;
import ro.fortsoft.pf4j.util.StringUtils;

/**
 *
 * @author Andrey
 */
@Extension
public class TicketsGroovyHook extends PatchsetHook {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitblitReceivePack.class);
    private GitBlit gitblit;
    private File groovyDir;
    private GroovyScriptEngine gse;
    private String gitblitUrl;

    public TicketsGroovyHook() throws IOException {
        gitblit = GitblitContext.getManager(GitBlit.class);
        groovyDir = gitblit.getHooksFolder();
        gitblitUrl = gitblit.getString(Keys.web.canonicalUrl, "");
        gse = new GroovyScriptEngine(groovyDir.getAbsolutePath());
    }

    @Override
    public void onNewPatchset(TicketModel ticket) {

    }

    @Override
    public void onUpdatePatchset(TicketModel ticket) {

    }

    @Override
    public void onMergePatchset(TicketModel ticket) {
        RepositoryModel repositoryModel = gitblit.getRepositoryModel(ticket.repository);
        Repository repository = gitblit.getRepository(repositoryModel.name);

        Collection<ReceiveCommand> commands = new ArrayList<>();
        
        Change lastMerged = null;
        
        for(Change change: ticket.changes){
            if(change.isMerge())
                lastMerged = change;
        }
        
        if(lastMerged == null){
            LOGGER.error("Cant find last merg change");
            return;
        }
        
        UserModel userModel = gitblit.getUserModel(lastMerged.author);
        
        if(userModel == null){
            LOGGER.error("Change author not exists");
            return;
        }
        
        RevWalk revWalk = null;
        try {
            revWalk = new RevWalk(repository);
            RevCommit branchTip = revWalk.lookupCommit(repository.resolve(ticket.mergeTo));
            ReceiveCommand receiveCommand = new ReceiveCommand(branchTip, branchTip, "refs/heads/" + ticket.mergeTo);
            commands.add(receiveCommand);
        } catch (IOException e) {
            LOGGER.error("Failed to determine merge branch", e);
        } finally {
            if (revWalk != null) {
                revWalk.release();
            }
        }

        Set<String> scripts = new LinkedHashSet<>();
        scripts.addAll(gitblit.getPostReceiveScriptsInherited(repositoryModel));
        if (!ArrayUtils.isEmpty(repositoryModel.postReceiveScripts)) {
            scripts.addAll(repositoryModel.postReceiveScripts);
        }

        runGroovy(commands, scripts, repositoryModel, userModel);
    }

    private void runGroovy(
            Collection<ReceiveCommand> commands,
            Set<String> scripts,
            RepositoryModel repository,
            UserModel user
    ) {
        if (scripts == null || scripts.isEmpty()) {
            // no Groovy scripts to execute
            return;
        }

        Binding binding = new Binding();
        binding.setVariable("gitblit", gitblit);
        binding.setVariable("repository", repository);
        binding.setVariable("user", user);
        binding.setVariable("commands", commands);
        binding.setVariable("url", gitblitUrl);
        binding.setVariable("logger", LOGGER);
        for (String script : scripts) {
            if (StringUtils.isEmpty(script)) {
                continue;
            }
            // allow script to be specified without .groovy extension
            // this is easier to read in the settings
            File file = new File(groovyDir, script);
            if (!file.exists() && !script.toLowerCase().endsWith(".groovy")) {
                file = new File(groovyDir, script + ".groovy");
                if (file.exists()) {
                    script = file.getName();
                }
            }
            try {
                Object result = gse.run(script, binding);
                if (result instanceof Boolean) {
                    if (!((Boolean) result)) {
                        LOGGER.error(MessageFormat.format(
                                "Groovy script {0} has failed!  Hook scripts aborted.", script));
                        break;
                    }
                }
            } catch (Exception e) {
                LOGGER.error(
                        MessageFormat.format("Failed to execute Groovy script {0}", script), e);
            }
        }
    }
}
