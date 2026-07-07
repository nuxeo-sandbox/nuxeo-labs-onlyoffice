package org.nuxeo.ecm.restapi.server.jaxrs;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OnlyOfficeCallback {

    @JsonProperty("changesurl")
    private String changesUrl = null;

    @JsonProperty("forcesavetype")
    private int forceSaveType = 0;

    @JsonProperty("key")
    private String key = null;

    @JsonProperty("status")
    private int status = 0;

    @JsonProperty("url")
    private String url = null;

    @JsonProperty("userdata")
    private String userData = null;

    @JsonProperty("users")
    private List<String> users = null;

    @JsonProperty("lastsave")
    private Date lastSave = null;

    // The `filetype` field (added to the OnlyOffice callback in v7.0) is the actual
    // extension of the document downloadable from `url`. When `assemblyFormatAsOrigin`
    // (server-side, default true since v7.0) fails to preserve the original format
    // (typical for legacy `.doc`), this field reports the fallback extension (usually
    // `.docx`). See B2 handling in OnlyOfficeResource.postCallback.
    @JsonProperty("filetype")
    private String fileType = null;

    // The ONLYOFFICE JWT (present when JWT is enabled on the Document Server). For
    // POST callbacks (7.1+) the token is sent in the body and its payload is the
    // whole callback. Verified in OnlyOfficeResource.postCallback; not part of
    // equals/hashCode/toString (it is an auth envelope, not callback data).
    @JsonProperty("token")
    private String token = null;

    public OnlyOfficeCallback() {
        super();
    }

    public String getChangesUrl() {
        return changesUrl;
    }

    public int getForceSaveType() {
        return forceSaveType;
    }

    public String getKey() {
        return key;
    }

    public int getStatus() {
        return status;
    }

    public String getUrl() {
        return url;
    }

    public String getUserData() {
        return userData;
    }

    public List<String> getUsers() {
        return users;
    }

    public Date getLastSave() {
        return lastSave;
    }

    public String getFileType() {
        return fileType;
    }

    public String getToken() {
        return token;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((changesUrl == null) ? 0 : changesUrl.hashCode());
        result = prime * result + forceSaveType;
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        result = prime * result + ((lastSave == null) ? 0 : lastSave.hashCode());
        result = prime * result + ((fileType == null) ? 0 : fileType.hashCode());
        result = prime * result + status;
        result = prime * result + ((url == null) ? 0 : url.hashCode());
        result = prime * result + ((userData == null) ? 0 : userData.hashCode());
        result = prime * result + ((users == null) ? 0 : users.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        OnlyOfficeCallback other = (OnlyOfficeCallback) obj;
        if (changesUrl == null) {
            if (other.changesUrl != null)
                return false;
        } else if (!changesUrl.equals(other.changesUrl))
            return false;
        if (forceSaveType != other.forceSaveType)
            return false;
        if (key == null) {
            if (other.key != null)
                return false;
        } else if (!key.equals(other.key))
            return false;
        if (lastSave == null) {
            if (other.lastSave != null)
                return false;
        } else if (!lastSave.equals(other.lastSave))
            return false;
        if (fileType == null) {
            if (other.fileType != null)
                return false;
        } else if (!fileType.equals(other.fileType))
            return false;
        if (status != other.status)
            return false;
        if (url == null) {
            if (other.url != null)
                return false;
        } else if (!url.equals(other.url))
            return false;
        if (userData == null) {
            if (other.userData != null)
                return false;
        } else if (!userData.equals(other.userData))
            return false;
        if (users == null) {
            if (other.users != null)
                return false;
        } else if (!users.equals(other.users))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return String.format(
                "OnlyOfficeCallback [changesUrl=%s, forceSaveType=%s, key=%s, status=%s, url=%s, userData=%s, users=%s, lastSave=%s, fileType=%s]",
                changesUrl, forceSaveType, key, status, url, userData, users, lastSave, fileType);
    }

}
