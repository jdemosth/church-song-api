CREATE TABLE IF NOT EXISTS service_plans (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    service_name TEXT NOT NULL,
    service_date DATE NOT NULL,
    service_time TIME
);

CREATE TABLE IF NOT EXISTS service_plan_songs (
    service_plan_id INTEGER NOT NULL,
    song_order INTEGER NOT NULL,
    song_id INTEGER NOT NULL,
    PRIMARY KEY (service_plan_id, song_order),
    FOREIGN KEY (service_plan_id) REFERENCES service_plans(id),
    FOREIGN KEY (song_id) REFERENCES song(id)
);

CREATE INDEX IF NOT EXISTS idx_service_plans_date_time
    ON service_plans(service_date, service_time);

CREATE INDEX IF NOT EXISTS idx_service_plan_songs_song
    ON service_plan_songs(song_id);
