package lk.ac.ruh.eng.campusevents.domain;

import java.util.HashSet;
import java.util.Set;
import org.bson.types.ObjectId;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("clubs")
public class Club extends AuditedDocument {
    @Indexed(unique = true)
    private String code;
    private String name;
    private String description;
    private Set<ObjectId> adminIds = new HashSet<>();

    public Set<ObjectId> getAdminIds() { return adminIds; }
    public void setAdminIds(Set<ObjectId> adminIds) { this.adminIds = adminIds; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
