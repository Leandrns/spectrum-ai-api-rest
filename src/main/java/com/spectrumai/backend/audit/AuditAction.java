package com.spectrumai.backend.audit;

/** A��es cr�ticas auditadas. */
public final class AuditAction {

    public static final String LOGIN_SUCCESS       = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE       = "LOGIN_FAILURE";
    public static final String LOGIN_BLOCKED       = "LOGIN_BLOCKED";
    public static final String USER_REGISTER       = "USER_REGISTER";
    public static final String USER_ROLE_CHANGED   = "USER_ROLE_CHANGED";
    public static final String SEARCH_CREATED      = "SEARCH_CREATED";
    public static final String SEARCH_EXPORTED     = "SEARCH_EXPORTED";
    public static final String SESSION_CREATED     = "SESSION_CREATED";
    public static final String CATALOG_IMPORTED    = "CATALOG_IMPORTED";
    public static final String CATALOG_CANCELLED   = "CATALOG_CANCELLED";
    public static final String DATA_PURGED         = "DATA_PURGED";

    private AuditAction() {}
}
