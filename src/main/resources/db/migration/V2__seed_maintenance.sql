-- Seed maintenance queues for each manager/technician
INSERT INTO maintenance_queues (username)
VALUES ('manager1')
ON CONFLICT (username) DO NOTHING;

-- Seed a few pending tasks in manager1's queue
INSERT INTO pending_tasks (vehicle_id, task_type, description, queue_id)
SELECT 6, 'OIL_CHANGE',     'Overdue oil change — FL-006 at 115k km', q.id FROM maintenance_queues q WHERE q.username = 'manager1'
UNION ALL
SELECT 1, 'ROUTINE_SERVICE','6-month service due for FL-001',         q.id FROM maintenance_queues q WHERE q.username = 'manager1'
UNION ALL
SELECT 8, 'TIRE_CHANGE',    'Front tyres worn — FL-008 needs replacement', q.id FROM maintenance_queues q WHERE q.username = 'manager1';
