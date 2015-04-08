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

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.gitblit.models.TeamModel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Manages repository permissions. Serializes a map of repository name to list
 * of allowed users and teams.
 *
 * @author plaflamme
 */
class RepositoryPermissionsManager {

   private final File store;

   private final ObjectMapper mapper = new ObjectMapper();

   private final Collection<TeamModel> teamModels;

   RepositoryPermissionsManager(final File store) {
      if (store == null)
         throw new IllegalArgumentException("store cannot be null");
      this.store = store;
      this.teamModels = load();

      SimpleModule module = new SimpleModule("TeamModelDeserializerModule", new Version(1, 0, 0, null));
      module.addDeserializer(PersistableTeamModel.class, new TeamModelDeserializer());
      mapper.registerModule(module);

   }

   public Collection<TeamModel> getTeamModels() {
      return teamModels;
   }

   synchronized List<String> repoTeams(final String repository) {
      List<String> teams = new ArrayList<String>();
      for (TeamModel t : teamModels) {
         if (t.hasRepositoryPermission(repository)) {
            teams.add(t.name);
         }
         ;
      }
      return teams;
   }

   private Collection<TeamModel> load() {
      if (store.exists()) {
         try {
            ArrayList<TeamModel> list = new ArrayList<TeamModel>();
            list.addAll(Arrays.asList(mapper.readValue(store, PersistableTeamModel[].class)));
            return list;

         } catch (JsonParseException e) {
            e.printStackTrace();
         } catch (JsonMappingException e) {
            e.printStackTrace();
         } catch (IOException e) {
            e.printStackTrace();
         }
      }
      return new ArrayList<TeamModel>();
   }

    public void updateTeamModels(final Collection<TeamModel> models) {
      try {
         ArrayList<TeamModel> tmpModels = new ArrayList<>(teamModels);
         tmpModels.forEach(t -> {
             models.forEach(teamToSave -> {
                 if (t.name.equals(teamToSave.name)) {
                     teamModels.remove(t);
                 }
                 teamModels.add(teamToSave);
             });
         });
         mapper.writeValue(store, teamModels);
      } catch (JsonGenerationException e) {
         e.printStackTrace();
      } catch (JsonMappingException e) {
         e.printStackTrace();
      } catch (IOException e) {
         e.printStackTrace();
      }
    }

}
