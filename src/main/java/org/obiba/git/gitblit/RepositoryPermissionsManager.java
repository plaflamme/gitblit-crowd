/*******************************************************************************
 * Copyright 2012(c) OBiBa. All rights reserved.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0.
 * 
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>.
 ******************************************************************************/
package org.obiba.git.gitblit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.thoughtworks.xstream.XStream;

/**
 * Manages repository permissions. Serializes a map of repository name to list of allowed users and teams.
 * 
 * @author plaflamme
 */
class RepositoryPermissionsManager {

  private static final Logger log = LoggerFactory.getLogger(RepositoryPermissionsManager.class);

  private final XStream xstream = new XStream();

  private final File store;

  private final Map<String, RepositoryPermissions> permissions;

  RepositoryPermissionsManager(File store) {
    if(store == null) throw new IllegalArgumentException("store cannot be null");
    this.store = store;
    xstream.alias("permissions", RepositoryPermissions.class);
    this.permissions = load();
  }

  synchronized List<String> repoUsers(String repository) {
    return ImmutableList.copyOf(repo(repository).users);
  }

  synchronized List<String> repoTeams(String repository) {
    return ImmutableList.copyOf(repo(repository).teams);
  }

  synchronized void populateTeamModel(TeamModel team) {
    for(String repo : permissions.keySet()) {
      if(repo(repo).teams.contains(team.name)) {
        team.repositories.add(repo);
      }
    }
  }

  synchronized void populateUserModel(UserModel user) {
    for(String repo : permissions.keySet()) {
      if(repo(repo).users.contains(user.username)) {
        user.repositories.add(repo);
      }
    }
  }

  synchronized void renameRepo(String original, String newName) {
    RepositoryPermissions repo = permissions.remove(original);
    if(repo != null) {
      permissions.put(newName, repo);
    }
    write();
  }

  synchronized void deleteRepo(String repository) {
    permissions.remove(repository);
    write();
  }

  synchronized void setRepoUsers(String repository, Iterable<String> users) {
    repo(repository).setUsers(users);
    write();
  }

  synchronized void setRepoTeams(String repository, Iterable<String> teams) {
    repo(repository).setTeams(teams);
    write();
  }

  private RepositoryPermissions repo(final String name) {
    if(permissions.containsKey(name) == false) {
      permissions.put(name, new RepositoryPermissions());
    }
    return permissions.get(name);
  }

  @SuppressWarnings("unchecked")
  private Map<String, RepositoryPermissions> load() {
    if(store.exists()) {
      return (Map<String, RepositoryPermissions>) xstream.fromXML(store);
    }
    return new HashMap<String, RepositoryPermissions>();
  }

  private void write() {
    OutputStream os = null;
    File temp = null;
    try {
      // make a temp file next to the perms file. This is because we use renameTo which expects files to live in the
      // same filesystem
      temp = File.createTempFile("gitblit", ".tmp", store.getParentFile());
      os = new FileOutputStream(temp);
      xstream.toXML(permissions, os);
      // Move new
      if(store.exists() && store.delete() == false) {
        // Do something
        log.error("could not delete perms file {}", store.getAbsolutePath());
      } else if(temp.renameTo(store) == false) {
        // Do something
        log.error("cannot rename temp file {} to {}", temp.getAbsolutePath(), store.getAbsolutePath());
      }
      temp.delete();
    } catch(IOException e) {
      log.error("Error while writing permissions file", e);
      throw new RuntimeException(e);
    } finally {
      try {
        if(os != null) os.close();
      } catch(IOException e) {
        // ignore
      }
      if(temp != null) temp.delete();
    }
  }

  private static class RepositoryPermissions {

    private Set<String> users = new HashSet<String>();

    private Set<String> teams = new HashSet<String>();

    public RepositoryPermissions setTeams(Iterable<String> teams) {
      this.teams.clear();
      Iterables.addAll(this.teams, teams);
      return this;
    }

    public RepositoryPermissions setUsers(Iterable<String> users) {
      this.users.clear();
      Iterables.addAll(this.users, users);
      return this;
    }
  }

}
