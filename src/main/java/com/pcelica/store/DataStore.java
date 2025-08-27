package com.pcelica.store;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.pcelica.model.BeeUser;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class DataStore {
    private static final Path DATA_DIR = Paths.get("data");
    private static final Path CSV_FILE = DATA_DIR.resolve("store.csv");
    private static final Path RESERVED_FILE = DATA_DIR.resolve("reserved_numbers.json");
    private final Map<Integer, List<BeeUser>> byYear = new HashMap<>();
    private final Map<Integer, Set<Integer>> reservedByYear = new HashMap<>();
    // startup snapshot (deep copies)
    private List<BeeUser> initialSnapshot = new ArrayList<>();
    private final Map<Integer, Set<Integer>> initialReservedByYear = new HashMap<>();
    private final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final DateTimeFormatter BK = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public DataStore() throws IOException {
        if (!Files.exists(DATA_DIR)) Files.createDirectories(DATA_DIR);
        if (!Files.exists(Paths.get("exports"))) Files.createDirectories(Paths.get("exports"));
        load();
    }

    private void load() throws IOException {
        // Load CSV if exists
        if (Files.exists(CSV_FILE)) {
            try (CSVReader reader = new CSVReader(new FileReader(CSV_FILE.toFile()))) {
                String[] header = reader.readNext(); // header
                String[] row;
                List<BeeUser> all = new ArrayList<>();
                while ((row = reader.readNext()) != null) {
                    try {
                        BeeUser u = new BeeUser();
                        u.setId(row[0]);
                        u.setFirstName(row[1]);
                        u.setLastName(row[2]);
                        u.setGender(row[3]);
                        u.setBirthDate(LocalDate.parse(row[4], DF));
                        u.setBirthPlace(row[5]);
                        u.setResidenceCity(row[6]);
                        u.setColonies(Integer.parseInt(row[7]));
                        u.setDocNumber(row[8]);
                        u.setSeqNumber(Integer.parseInt(row[9]));
                        u.setYear(Integer.parseInt(row[10]));
                        // Handle certificate date - check if column exists
                        if (row.length > 11 && row[11] != null && !row[11].isEmpty()) {
                            u.setCertificateDate(LocalDate.parse(row[11], DF));
                        }
                        all.add(u);
                        byYear.computeIfAbsent(u.getYear(), k -> new ArrayList<>()).add(u);
                        if (u.getSeqNumber() > 0) reservedByYear.computeIfAbsent(u.getYear(), k -> new HashSet<>()).add(u.getSeqNumber());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                initialSnapshot = all.stream().map(this::deepCopy).collect(Collectors.toList());
                createBackupFile("STARTUP");
            } catch (com.opencsv.exceptions.CsvValidationException e) {
                throw new IOException("Greška pri validaciji CSV fajla", e);
            }
        }

        // Load reserved numbers (if any)
        if (Files.exists(RESERVED_FILE)) {
            String json = new String(Files.readAllBytes(RESERVED_FILE));
            Gson g = new Gson();
            Type t = new TypeToken<Map<String, List<Integer>>>(){}.getType();
            Map<String, List<Integer>> map = g.fromJson(json, t);
            if (map != null) {
                for (Map.Entry<String, List<Integer>> e : map.entrySet()) {
                    int y = Integer.parseInt(e.getKey());
                    reservedByYear.put(y, new HashSet<>(e.getValue()));
                }
            }
        }
        // Save initial reserved snapshot (deep copy)
        for (Map.Entry<Integer, Set<Integer>> e : reservedByYear.entrySet()) {
            initialReservedByYear.put(e.getKey(), new HashSet<>(e.getValue()));
        }
    }

    private BeeUser deepCopy(BeeUser u) {
        BeeUser copy = new BeeUser();
        copy.setId(u.getId());
        copy.setFirstName(u.getFirstName());
        copy.setLastName(u.getLastName());
        copy.setGender(u.getGender());
        copy.setBirthDate(u.getBirthDate());
        copy.setBirthPlace(u.getBirthPlace());
        copy.setResidenceCity(u.getResidenceCity());
        copy.setColonies(u.getColonies());
        copy.setDocNumber(u.getDocNumber());
        copy.setSeqNumber(u.getSeqNumber());
        copy.setYear(u.getYear());
        return copy;
    }

    /**
     * Reserve next sequence for given year (keeps reservedByYear persistent)
     */
    public synchronized int reserveNext(int year) {
        Set<Integer> set = reservedByYear.computeIfAbsent(year, k -> new HashSet<>());
        int next = 1;
        while (set.contains(next)) next++;
        set.add(next);
        saveReserved();
        return next;
    }

    private void saveReserved() {
        try (Writer w = Files.newBufferedWriter(RESERVED_FILE)) {
            Map<String, List<Integer>> out = new HashMap<>();
            for (Map.Entry<Integer, Set<Integer>> e : reservedByYear.entrySet()) {
                List<Integer> l = new ArrayList<>(e.getValue());
                Collections.sort(l);
                out.put(String.valueOf(e.getKey()), l);
            }
            new Gson().toJson(out, w);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Persist store.csv and reserved_numbers.json and create timestamped backup file with 'reason' in filename.
     * Use reason values like: ADD, EDIT, DELETE, CLOSE, IMPORT, IMPORT_REPLACE, STARTUP...
     */
    private void persistAll(String reason) {
        try (CSVWriter writer = new CSVWriter(new FileWriter(CSV_FILE.toFile()))) {
            String[] header = {"id","firstName","lastName","gender","birthDate","birthPlace","residenceCity","colonies","docNumber","seqNumber","year","certificateDate"};
            writer.writeNext(header);
            for (List<BeeUser> list : byYear.values()) {
                for (BeeUser u : list) {
                    String[] row = {
                            u.getId(),
                            u.getFirstName(),
                            u.getLastName(),
                            u.getGender(),
                            u.getBirthDate().format(DF),
                            u.getBirthPlace(),
                            u.getResidenceCity(),
                            String.valueOf(u.getColonies()),
                            u.getDocNumber(),
                            String.valueOf(u.getSeqNumber()),
                            String.valueOf(u.getYear()),
                            u.getCertificateDate() != null ? u.getCertificateDate().format(DF) : ""
                    };
                    writer.writeNext(row);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        saveReserved();
        createBackupFile(reason);
    }

    private void createBackupFile(String reason) {
        try {
            String stamp = LocalDateTime.now().format(BK);
            String fname = String.format("backup_%s_%s.csv", stamp, reason == null ? "AUTO" : reason);
            Path out = DATA_DIR.resolve(fname);
            // copy store.csv to backup if exists
            if (Files.exists(CSV_FILE)) {
                Files.copy(CSV_FILE, out, StandardCopyOption.REPLACE_EXISTING);
            } else {
                // if CSV doesn't exist yet, write an empty CSV with header for consistency
                try (CSVWriter writer = new CSVWriter(new FileWriter(out.toFile()))) {
                    writer.writeNext(new String[]{"id","firstName","lastName","gender","birthDate","birthPlace","residenceCity","colonies","docNumber","seqNumber","year"});
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Public API to force save (used on app close)
    public synchronized void saveAll() {
        persistAll("CLOSE");
    }

    public synchronized void addUser(BeeUser user) {
        byYear.computeIfAbsent(user.getYear(), k -> new ArrayList<>()).add(user);
        persistAll("ADD");
    }

    public synchronized void updateUser(BeeUser user) {
        List<BeeUser> list = byYear.getOrDefault(user.getYear(), new ArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(user.getId())) {
                list.set(i, user);
                persistAll("EDIT");
                return;
            }
        }
    }

    public synchronized void deleteUser(BeeUser user) {
        List<BeeUser> list = byYear.getOrDefault(user.getYear(), new ArrayList<>());
        list.removeIf(u -> u.getId().equals(user.getId()));
        // per requirement: do NOT free reserved seq number unless importing with replace behavior
        persistAll("DELETE");
    }

    public List<BeeUser> getForYear(int year) {
        return new ArrayList<>(byYear.getOrDefault(year, new ArrayList<>()));
    }

    public Set<Integer> getReservedForYear(int year) {
        return reservedByYear.getOrDefault(year, Collections.emptySet());
    }

    public boolean existsSameName(int year, String firstName, String lastName) {
        return byYear.getOrDefault(year, Collections.emptyList())
                .stream()
                .anyMatch(u -> u.getFirstName().equalsIgnoreCase(firstName.trim()) &&
                        u.getLastName().equalsIgnoreCase(lastName.trim()));
    }

    public Set<Integer> getYears() {
        Set<Integer> keys = new TreeSet<>(byYear.keySet());
        keys.add(LocalDate.now().getYear());
        return keys;
    }

    /**
     * Vraća deep-copy snapshot stanja pri startu aplikacije.
     */
    public synchronized void restoreSnapshot() throws IOException {
        if (initialSnapshot == null) return;
        byYear.clear();
        for (BeeUser u : initialSnapshot) {
            BeeUser copy = deepCopy(u);
            byYear.computeIfAbsent(copy.getYear(), k -> new ArrayList<>()).add(copy);
        }
        // restore reserved numbers to initial snapshot (deep copy)
        reservedByYear.clear();
        for (Map.Entry<Integer, Set<Integer>> e : initialReservedByYear.entrySet()) {
            reservedByYear.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        persistAll("RESTORE_STARTUP");
    }

    public List<BeeUser> getAllUsers() {
        List<BeeUser> all = new ArrayList<>();
        for (List<BeeUser> l : byYear.values()) all.addAll(l);
        return all;
    }

    public BeeUser getLatestByNameBefore(String firstName, String lastName, int beforeYear) {
        BeeUser best = null;
        for (BeeUser u : getAllUsers()) {
            if (u.getFirstName().equalsIgnoreCase(firstName.trim()) &&
                    u.getLastName().equalsIgnoreCase(lastName.trim()) &&
                    u.getYear() < beforeYear) {
                if (best == null || u.getYear() > best.getYear()) {
                    best = u;
                }
            }
        }
        return best;
    }

    /**
     * Parsira arbitrarni CSV u isti model i vraća listu BeeUser (ne mijenja trenutni store).
     */
    public List<BeeUser> readCsv(Path file) throws IOException {
        List<BeeUser> all = new ArrayList<>();
        if (!Files.exists(file)) return all;
        try (CSVReader reader = new CSVReader(new FileReader(file.toFile()))) {
            String[] header = reader.readNext();
            String[] row;
            while ((row = reader.readNext()) != null) {
                try {
                    BeeUser u = new BeeUser();
                    u.setId(row[0]);
                    u.setFirstName(row[1]);
                    u.setLastName(row[2]);
                    u.setGender(row[3]);
                    u.setBirthDate(LocalDate.parse(row[4], DF));
                    u.setBirthPlace(row[5]);
                    u.setResidenceCity(row[6]);
                    u.setColonies(Integer.parseInt(row[7]));
                    u.setDocNumber(row[8]);
                    u.setSeqNumber(Integer.parseInt(row[9]));
                    u.setYear(Integer.parseInt(row[10]));
                    all.add(u);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } catch (com.opencsv.exceptions.CsvValidationException e) {
            throw new IOException("CSV validation error", e);
        }
        return all;
    }

    /**
     * Uvozi CSV snapshot kao novi glavni snapshot **(staro ponašanje)**:
     * - postavlja byYear iz snapshot-a
     * - **spaja** (union) rezervisane brojeve snapshot-a s trenutnim reservedByYear (ne briše ništa)
     */
    public synchronized void importSnapshotAsMain(Path file) throws IOException {
        List<BeeUser> parsed = readCsv(file);
        Map<Integer, Set<Integer>> parsedReserved = new HashMap<>();
        for (BeeUser u : parsed) {
            if (u.getSeqNumber() > 0) {
                parsedReserved.computeIfAbsent(u.getYear(), k -> new HashSet<>()).add(u.getSeqNumber());
            }
        }

        // set byYear from parsed
        byYear.clear();
        for (BeeUser u : parsed) {
            byYear.computeIfAbsent(u.getYear(), k -> new ArrayList<>()).add(u);
        }

        // union reserved numbers with current reservedByYear
        for (Map.Entry<Integer, Set<Integer>> e : parsedReserved.entrySet()) {
            Set<Integer> target = reservedByYear.computeIfAbsent(e.getKey(), k -> new HashSet<>());
            target.addAll(e.getValue());
        }

        // update initial snapshot (so restoreSnapshot returns here)
        initialSnapshot = parsed.stream().map(this::deepCopy).collect(Collectors.toList());
        initialReservedByYear.clear();
        for (Map.Entry<Integer, Set<Integer>> e : reservedByYear.entrySet()) {
            initialReservedByYear.put(e.getKey(), new HashSet<>(e.getValue()));
        }

        persistAll("IMPORT");
    }

    /**
     * Uvozi CSV snapshot kao novi glavni snapshot **(novo ponašanje - REPLACE reserved)**:
     * - postavlja byYear iz snapshot-a
     * - **zamjenjuje** trenutne reservedByYear s onim iz snapshot-a (tj. brišu se rezervacije koje su nastale kasnije)
     * - ažurira initialSnapshot i initialReservedByYear tako da "Restore startup" vraća ovdje
     *
     * Posljedica: nakon uvoza, novi dodani pčelar će dobiti sljedeći broj nakon najvećeg seq-a prisutnog u tom snapshotu.
     */
    public synchronized void importSnapshotAsMainReplaceReserved(Path file) throws IOException {
        List<BeeUser> parsed = readCsv(file);
        Map<Integer, Set<Integer>> parsedReserved = new HashMap<>();
        for (BeeUser u : parsed) {
            if (u.getSeqNumber() > 0) {
                parsedReserved.computeIfAbsent(u.getYear(), k -> new HashSet<>()).add(u.getSeqNumber());
            }
        }

        // set byYear from parsed
        byYear.clear();
        for (BeeUser u : parsed) {
            byYear.computeIfAbsent(u.getYear(), k -> new ArrayList<>()).add(u);
        }

        // REPLACE reservedByYear with parsedReserved (this frees numbers that are not in parsedReserved)
        reservedByYear.clear();
        for (Map.Entry<Integer, Set<Integer>> e : parsedReserved.entrySet()) {
            reservedByYear.put(e.getKey(), new HashSet<>(e.getValue()));
        }

        // update initial snapshot and initial reserved
        initialSnapshot = parsed.stream().map(this::deepCopy).collect(Collectors.toList());
        initialReservedByYear.clear();
        for (Map.Entry<Integer, Set<Integer>> e : reservedByYear.entrySet()) {
            initialReservedByYear.put(e.getKey(), new HashSet<>(e.getValue()));
        }

        persistAll("IMPORT_REPLACE");
    }
}
