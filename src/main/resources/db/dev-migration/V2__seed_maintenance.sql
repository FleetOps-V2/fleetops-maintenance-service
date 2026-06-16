INSERT INTO maintenance_queues (id, username) VALUES 
(1, 'driver10'),
(2, 'driver12')
ON CONFLICT (username) DO NOTHING;

INSERT INTO pending_tasks (vehicle_id, task_type, description, queue_id) VALUES
(10, 'ROUTINE_SERVICE', 'Routine 50,000 km general maintenance and multi-point inspection.', 1),
(12, 'BREAKDOWN',       'Engine overheating alert near Highway 45. Needs towing.', 2);

SELECT setval('maintenance_queues_id_seq', (SELECT MAX(id) FROM maintenance_queues));
SELECT setval('pending_tasks_id_seq', (SELECT MAX(id) FROM pending_tasks));
