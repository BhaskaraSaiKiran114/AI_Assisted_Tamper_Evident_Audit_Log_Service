GRANT SELECT, INSERT ON lifecycle_action, event_archive, payload_redaction TO audit_runtime;

-- No broad runtime DELETE privilege. Require matching committed administrative evidence/marker.
CREATE FUNCTION public.erase_audit_field_key(target_id UUID, target_path TEXT, operation UUID)
RETURNS INTEGER
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE erased INTEGER;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.payload_redaction r
        JOIN public.lifecycle_action a ON a.operation_id = r.operation_id
        JOIN public.audit_event e ON e.event_id = a.audit_event_id
        WHERE r.event_id = target_id AND r.field_path = target_path AND r.operation_id = operation
          AND a.action_type = 'REDACTION'
          AND e.event_type = 'AUDIT_INTERNAL_REDACTION' AND e.resource_type = 'AUDIT_LIFECYCLE'
          AND e.resource_id = operation::TEXT AND e.actor_id = a.actor_id
          AND a.detail_json::jsonb ->> 'targetEventId' = target_id::TEXT
          AND (a.detail_json::jsonb -> 'affectedPaths') ? target_path
          AND e.payload_json::jsonb = a.detail_json::jsonb
    ) THEN
        RAISE EXCEPTION 'Audited redaction evidence required' USING ERRCODE = '42501';
    END IF;
    DELETE FROM public.payload_key WHERE event_id = target_id AND field_path = target_path;
    GET DIAGNOSTICS erased = ROW_COUNT;
    RETURN erased;
END
$$;
REVOKE ALL ON FUNCTION public.erase_audit_field_key(UUID, TEXT, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.erase_audit_field_key(UUID, TEXT, UUID) TO audit_runtime;
