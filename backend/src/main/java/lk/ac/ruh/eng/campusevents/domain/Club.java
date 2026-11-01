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
    private String shortName;
    private String logoUrl = "";
    private String contactEmail = "";
    private ClubStatus status = ClubStatus.ACTIVE;
    @Indexed(unique = true, sparse = true)
    private String nameKey;
    @Indexed(unique = true, sparse = true)
    private String shortNameKey;

    public String getShortName() { return shortName == null ? code : shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; this.code = shortName; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public ClubStatus getStatus() { return status == null ? ClubStatus.ACTIVE : status; }
    public void setStatus(ClubStatus status) { this.status = status; }
    public String getNameKey() { return nameKey; }
    public void setNameKey(String nameKey) { this.nameKey = nameKey; }
    public String getShortNameKey() { return shortNameKey; }
    public void setShortNameKey(String shortNameKey) { this.shortNameKey = shortNameKey; }
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
