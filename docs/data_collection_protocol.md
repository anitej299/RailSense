# Real Smartphone Data Collection Protocol

1. Obtain written operator/institutional permission and ethics review where applicable.
2. Secure the phone so it cannot become a projectile and never access restricted railway areas.
3. Select a pseudonymous journey, route, and placement label; do not record passenger identity.
4. Synchronize phone time, record the device model and nominal sensor rate, then start before motion.
5. Keep placement unchanged within a journey. Record changes as a new placement/journey.
6. Stop and export CSV. Store field notes separately without personal data.
7. Label evidence provenance. Inspection-confirmed labels must be supplied by authorized experts; vibration peaks alone are not defect labels.

Imported CSV must satisfy `src/data/schema.py`. Place it in `data/raw/`, preserve `data_source=REAL_WORLD`, and run the same preprocessing while using journey-level splits.

