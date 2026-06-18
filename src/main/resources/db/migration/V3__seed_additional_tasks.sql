-- Add more pending tasks covering new vehicles FL-011 to FL-015
INSERT INTO pending_tasks (vehicle_id, task_type, description, queue_id)
SELECT 14, 'ROUTINE_SERVICE', 'FL-014 Mahindra Scorpio — 6-month service overdue',    q.id FROM maintenance_queues q WHERE q.username = 'manager1'
UNION ALL
SELECT 12, 'OIL_CHANGE',     'FL-012 Ford Ranger — oil change at 48k km',             q.id FROM maintenance_queues q WHERE q.username = 'manager1'
UNION ALL
SELECT 15, 'BATTERY',        'FL-015 Ashok Leyland Partner — battery health check',   q.id FROM maintenance_queues q WHERE q.username = 'manager1';
