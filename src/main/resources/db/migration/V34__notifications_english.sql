-- Translate legacy French notification copy to English

UPDATE notifications
SET title = 'New content available',
    message = CASE
        WHEN message ~ 'Contenu n°\s*[0-9]+' AND message ~ '«[^»]+»' THEN
            'Your agent delivered Content #' ||
            substring(message FROM 'Contenu n°\s*([0-9]+)') ||
            ' for "' || substring(message FROM '«([^»]+)»') || '".'
        WHEN message ~ '«[^»]+»' THEN
            'Your agent delivered content for "' || substring(message FROM '«([^»]+)»') || '".'
        ELSE 'Your agent delivered new content.'
    END
WHERE type = 'CONTENT_DELIVERED';

UPDATE notifications
SET title = 'Ecosystem activated',
    message = CASE
        WHEN message ~ '«[^»]+»' THEN
            'Your ecosystem "' || substring(message FROM '«([^»]+)»') ||
            '" is active! Set up your publishing schedule.'
        ELSE 'Your ecosystem is active! Set up your publishing schedule.'
    END
WHERE type = 'ECOSYSTEM_ACTIVE';

UPDATE notifications
SET title = 'Validation model ready',
    message = CASE
        WHEN message ~ '«[^»]+»' THEN
            'Your agent published the validation model for "' ||
            substring(message FROM '«([^»]+)»') || '". Review and approve it.'
        ELSE 'Your validation model is ready. Review and approve it.'
    END
WHERE type = 'DEMO_READY';

UPDATE notifications
SET title = 'Request submitted',
    message = CASE
        WHEN message ~ '«[^»]+»' THEN
            'Your niche "' || substring(message FROM '«([^»]+)»') ||
            '" is being processed. You will be notified when the validation model is ready.'
        ELSE 'Your niche is being processed. You will be notified when the validation model is ready.'
    END
WHERE type = 'NICHE_PENDING_MODEL';

UPDATE notifications
SET title = 'Niche awaiting validation model',
    message = CASE
        WHEN message ~ '«[^»]+»' THEN
            'The client confirmed "' || substring(message FROM '«([^»]+)»') ||
            '". Prepare the validation model.'
        ELSE 'A client niche is ready for validation model preparation.'
    END
WHERE type IN ('NICHE_WAITING_VALIDATION', 'NICHE_REQUEST_NEW');

UPDATE notifications
SET title = 'Niche activated by client',
    message = CASE
        WHEN message ~ '«[^»]+»' THEN
            'The client activated the ecosystem "' || substring(message FROM '«([^»]+)»') ||
            '". You can deliver content.'
        ELSE 'A client activated their ecosystem. You can deliver content.'
    END
WHERE type = 'NICHE_ACTIVATED';

UPDATE notifications
SET title = 'Validation model rejected'
WHERE type = 'DEMO_REJECTED';

UPDATE notifications
SET title = 'Payment failed',
    message = 'Your ecosystem subscription payment failed. Please try again.'
WHERE type = 'PAYMENT_FAILED';

UPDATE notifications
SET title = 'Published successfully'
WHERE type = 'POST_PUBLISHED';

UPDATE notifications
SET title = 'Publication failed'
WHERE type = 'POST_FAILED';
