package org.obiba.git.gitblit;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.gitblit.Constants;

import java.io.IOException;
import java.util.Iterator;

public class TeamModelDeserializer extends JsonDeserializer<PersistableTeamModel> {

    @Override
    public PersistableTeamModel deserialize(final JsonParser p, final DeserializationContext ctx) throws IOException,
            JsonProcessingException {
        ObjectCodec oc = p.getCodec();
        JsonNode node = oc.readTree(p);

        PersistableTeamModel m = new PersistableTeamModel(node.get("name").asText());
        m.accountType = Constants.AccountType.fromString(node.get("accountType").asText());
        m.canAdmin = node.get("canAdmin").asBoolean();
        m.canFork = node.get("canFork").asBoolean();
        m.canCreate = node.get("canCreate").asBoolean();

        JsonNode permissionsNode = node.get("permissions");
        Iterator<String> names = permissionsNode.fieldNames();
        while (names.hasNext()) {
            String n = names.next();
            m.permissions.put(n, Constants.AccessPermission.fromCode(permissionsNode.get(n).asText()));
        }

        return m;
    }
}
