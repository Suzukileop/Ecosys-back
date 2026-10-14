package com.plateforme.user.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Curated specialty suggestions for the profile editor autocomplete, grouped by field.
 * Suggestions only — specialties remain free text (see {@link SpecialtyTaxonomy}).
 */
public final class SpecialtyCatalog {

    /** Field → (search keywords incl. French, specialties). */
    private record Field(List<String> keywords, List<String> specialties) {}

    private static final Map<String, Field> FIELDS = buildFields();

    /** Every catalog label once, in field order. */
    private static final List<String> ALL = FIELDS.values().stream()
            .flatMap(field -> field.specialties().stream())
            .distinct()
            .toList();

    private SpecialtyCatalog() {}

    public static List<String> all() {
        return ALL;
    }

    /**
     * Catalog labels matching the query, best first: exact, prefix, word prefix, substring,
     * then labels whose field matches (typing "design" also proposes "Motion designer").
     */
    public static List<String> search(String query, int limit) {
        String needle = key(query == null ? "" : query);
        if (needle.isEmpty() || limit <= 0) {
            return List.of();
        }
        String compactNeedle = needle.replaceAll("[^a-z0-9]+", "");
        LinkedHashMap<String, Integer> ranked = new LinkedHashMap<>();
        for (String label : ALL) {
            int rank = rank(label, needle, compactNeedle);
            if (rank < Integer.MAX_VALUE) {
                ranked.put(label, rank);
            }
        }
        for (Map.Entry<String, Field> entry : FIELDS.entrySet()) {
            if (!fieldMatches(entry.getKey(), entry.getValue(), needle)) {
                continue;
            }
            for (String label : entry.getValue().specialties()) {
                ranked.putIfAbsent(label, 5);
            }
        }
        List<String> labels = new ArrayList<>(ranked.keySet());
        labels.sort(Comparator
                .comparingInt((String label) -> ranked.get(label))
                .thenComparingInt(String::length)
                .thenComparing(label -> label.toLowerCase(Locale.ROOT)));
        return labels.size() > limit ? List.copyOf(labels.subList(0, limit)) : List.copyOf(labels);
    }

    /** 0 exact, 1 prefix, 2 word prefix, 3 substring, 4 compact substring, MAX no match. */
    public static int rank(String label, String needle, String compactNeedle) {
        String haystack = key(label);
        if (haystack.equals(needle)) {
            return 0;
        }
        if (haystack.startsWith(needle)) {
            return 1;
        }
        for (String word : haystack.split("[^a-z0-9+#.]+")) {
            if (!word.isEmpty() && word.startsWith(needle)) {
                return 2;
            }
        }
        if (haystack.contains(needle)) {
            return 3;
        }
        if (compactNeedle.length() >= 2 && haystack.replaceAll("[^a-z0-9]+", "").contains(compactNeedle)) {
            return 4;
        }
        return Integer.MAX_VALUE;
    }

    private static boolean fieldMatches(String name, Field field, String needle) {
        if (needle.length() < 3) {
            return false;
        }
        if (key(name).startsWith(needle)) {
            return true;
        }
        for (String keyword : field.keywords()) {
            String k = key(keyword);
            if (k.startsWith(needle) || (needle.length() >= 4 && needle.startsWith(k))) {
                return true;
            }
        }
        return false;
    }

    public static String key(String value) {
        return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Map<String, Field> buildFields() {
        Map<String, Field> m = new LinkedHashMap<>();
        m.put("Software development", new Field(
                List.of("developer", "development", "dev", "software", "programming", "coding", "engineer",
                        "developpeur", "developpement", "programmeur", "informatique", "ingenieur"),
                List.of("Developer", "Software engineer", "Full-stack developer", "Frontend developer",
                        "Backend developer", "Web developer", "Mobile developer", "iOS developer",
                        "Android developer", "Flutter developer", "React Native developer", "React developer",
                        "Next.js developer", "Vue.js developer", "Angular developer", "Svelte developer",
                        "Node.js developer", "Java developer", "Spring Boot developer", "Kotlin developer",
                        "Python developer", "Django developer", "PHP developer", "Laravel developer",
                        "Symfony developer", "Ruby on Rails developer", "Go developer", "Rust developer",
                        "C# / .NET developer", "C++ developer", "TypeScript developer", "JavaScript developer",
                        "WordPress developer", "Shopify developer", "Webflow developer", "No-code developer",
                        "Low-code developer", "API developer", "Embedded systems engineer", "Firmware engineer",
                        "Desktop application developer", "Chrome extension developer", "Game developer",
                        "Unity developer", "Unreal Engine developer", "Godot developer", "Software architect",
                        "Solutions architect", "Technical lead", "CTO as a service", "Mobile app developer",
                        "Progressive web app developer", "E-commerce developer", "CMS developer",
                        "Salesforce developer", "SAP consultant", "ERP consultant", "Odoo developer",
                        "Integration engineer", "Automation engineer", "Scripting & automation",
                        "Bot developer", "Telegram bot developer", "Discord bot developer")));
        m.put("Data & AI", new Field(
                List.of("data", "ai", "ia", "artificial intelligence", "intelligence artificielle", "machine learning",
                        "ml", "analytics", "donnees", "statistique"),
                List.of("Data science", "Data scientist", "Data analyst", "Data engineer", "Big data engineer",
                        "Data architect", "Analytics engineer", "Business intelligence analyst",
                        "Power BI developer", "Tableau developer", "Looker developer", "Data visualization",
                        "Data annotation", "Data entry", "Data cleaning", "Web scraping", "ETL developer",
                        "Database administrator", "SQL developer", "Machine learning engineer", "AI engineer",
                        "Deep learning engineer", "Computer vision engineer", "NLP engineer", "LLM engineer",
                        "Prompt engineer", "Generative AI specialist", "AI automation specialist",
                        "AI chatbot developer", "MLOps engineer", "Statistician", "Quantitative analyst",
                        "Research scientist", "Bioinformatician", "GIS analyst", "Geospatial data analyst",
                        "Excel expert", "Google Sheets expert", "Spreadsheet automation")));
        m.put("Cloud, DevOps & infrastructure", new Field(
                List.of("devops", "cloud", "infrastructure", "ops", "sysadmin", "network", "reseau", "serveur",
                        "server", "kubernetes", "docker", "aws", "azure", "gcp"),
                List.of("DevOps", "DevOps engineer", "Site reliability engineer", "Cloud engineer",
                        "Cloud architect", "AWS engineer", "Azure engineer", "Google Cloud engineer",
                        "Kubernetes engineer", "Docker specialist", "Platform engineer", "CI/CD engineer",
                        "Infrastructure as code", "Terraform engineer", "Linux system administrator",
                        "Windows system administrator", "System administrator", "Network engineer",
                        "Network administrator", "IT support", "IT technician", "Helpdesk technician",
                        "IT consultant", "Database engineer", "Release engineer", "Build engineer",
                        "Observability engineer", "FinOps specialist")));
        m.put("Cybersecurity", new Field(
                List.of("security", "cyber", "securite", "hacking", "pentest", "infosec"),
                List.of("Cybersecurity", "Cybersecurity analyst", "Penetration tester", "Ethical hacker",
                        "Security engineer", "Application security engineer", "Cloud security engineer",
                        "SOC analyst", "Security consultant", "GRC consultant", "Incident responder",
                        "Malware analyst", "Digital forensics", "Bug bounty hunter", "Smart contract auditor")));
        m.put("Web3 & emerging tech", new Field(
                List.of("blockchain", "web3", "crypto", "nft", "ar", "vr", "xr", "iot", "robot"),
                List.of("Blockchain developer", "Smart contract developer", "Solidity developer",
                        "Web3 developer", "NFT artist", "Crypto community manager", "AR developer",
                        "VR developer", "XR developer", "Metaverse designer", "IoT engineer",
                        "Robotics engineer", "Drone pilot", "Quantum computing researcher")));
        m.put("QA & product", new Field(
                List.of("qa", "test", "testing", "quality", "product", "produit", "project", "projet", "agile", "scrum"),
                List.of("QA engineer", "QA tester", "Manual tester", "Test automation engineer",
                        "Game tester", "Accessibility specialist", "Performance engineer", "Product manager",
                        "Product owner", "Technical product manager", "Project manager", "Scrum master",
                        "Agile coach", "Program manager", "Delivery manager", "Business analyst",
                        "Technical writer", "Developer advocate", "Developer community manager")));
        m.put("Design", new Field(
                List.of("design", "designer", "graphic", "graphique", "graphiste", "visual", "visuel", "creative",
                        "creatif"),
                List.of("Design", "Graphic designer", "Visual designer", "Brand designer", "Logo designer",
                        "Web designer", "Print designer", "Packaging designer", "Editorial designer",
                        "Layout designer", "Presentation designer", "Pitch deck designer", "Infographic designer",
                        "Social media designer", "Thumbnail designer", "Poster designer", "Book cover designer",
                        "Album cover designer", "Merch designer", "T-shirt designer", "Sticker designer",
                        "Icon designer", "Typeface designer", "Lettering artist", "Calligrapher",
                        "Signage designer", "Exhibition designer", "Environmental graphic designer",
                        "Event designer", "Wedding stationery designer", "Creative director", "Art director",
                        "Design system designer", "Email designer", "Banner ad designer", "Canva designer",
                        "Figma designer", "Photoshop expert", "Illustrator expert", "InDesign expert")));
        m.put("UI / UX & product design", new Field(
                List.of("ui", "ux", "user experience", "user interface", "interface", "product design", "app design"),
                List.of("UI / UX", "UI designer", "UX designer", "UX researcher", "UX writer",
                        "Product designer", "Interaction designer", "Mobile app designer", "Dashboard designer",
                        "SaaS designer", "Landing page designer", "Wireframing", "Prototyping",
                        "Usability tester", "Information architect", "Service designer", "Conversational designer",
                        "Design ops", "Webflow designer", "Framer designer")));
        m.put("Branding", new Field(
                List.of("brand", "branding", "identite", "identity", "marque", "naming"),
                List.of("Branding", "Brand strategist", "Brand identity designer", "Naming specialist",
                        "Visual identity designer", "Brand guidelines designer", "Rebranding specialist")));
        m.put("Illustration & art", new Field(
                List.of("illustration", "illustrator", "illustrateur", "art", "artist", "artiste", "dessin",
                        "drawing", "painting", "peinture", "comic", "bd", "manga"),
                List.of("Illustration", "Illustrator", "Digital illustrator", "Children's book illustrator",
                        "Editorial illustrator", "Concept artist", "Character designer", "Comic artist",
                        "Manga artist", "Storyboard artist", "Tattoo designer", "Portrait artist",
                        "Caricature artist", "Pixel artist", "Vector illustrator", "Botanical illustrator",
                        "Medical illustrator", "Technical illustrator", "Map illustrator", "Mural artist",
                        "Painter", "Watercolor artist", "Fine artist", "Sculptor", "Street artist",
                        "Generative artist", "AI artist", "Emoji designer", "Mascot designer")));
        m.put("3D & animation", new Field(
                List.of("3d", "animation", "animator", "animateur", "motion", "vfx", "cgi", "modeling",
                        "modelisation", "blender", "render", "rendu"),
                List.of("3D", "3D artist", "3D modeler", "3D animator", "3D generalist", "3D product visualization",
                        "3D architectural visualization", "3D character artist", "3D environment artist",
                        "3D printing designer", "Texture artist", "Rigging artist", "Lighting artist",
                        "Blender artist", "Cinema 4D artist", "Maya artist", "ZBrush sculptor",
                        "Game artist", "Game designer", "Level designer", "Motion designer",
                        "Motion graphics artist", "2D animator", "Explainer video animator",
                        "Whiteboard animator", "Stop motion animator", "Logo animator", "Lottie animator",
                        "VFX artist", "Compositor", "CGI artist", "Animation director")));
        m.put("Video & film", new Field(
                List.of("video", "film", "cinema", "montage", "monteur", "editor", "youtube", "tiktok", "reels"),
                List.of("Video editor", "Videographer", "Filmmaker", "Director", "Cinematographer",
                        "Director of photography", "Camera operator", "Drone videographer", "Colorist",
                        "Video producer", "Documentary filmmaker", "Wedding videographer",
                        "Event videographer", "Music video director", "Commercial director",
                        "YouTube video editor", "Short-form video editor", "TikTok video editor",
                        "Reels editor", "Podcast video editor", "Gaming video editor", "Trailer editor",
                        "Subtitler", "Captioning specialist", "Screenwriter", "Scriptwriter",
                        "Storyboarder", "Film producer", "Production assistant", "Gaffer", "Set designer",
                        "Production designer", "Costume designer", "Makeup artist", "Special effects makeup artist",
                        "Live streaming producer", "Video content creator", "UGC creator")));
        m.put("Photography", new Field(
                List.of("photo", "photography", "photographe", "photographer", "camera", "shooting", "retouche",
                        "retouching"),
                List.of("Photography", "Photographer", "Portrait photographer", "Wedding photographer",
                        "Event photographer", "Product photographer", "Food photographer",
                        "Fashion photographer", "Real estate photographer", "Architectural photographer",
                        "Landscape photographer", "Travel photographer", "Street photographer",
                        "Sports photographer", "Wildlife photographer", "Photojournalist",
                        "Commercial photographer", "Headshot photographer", "Newborn photographer",
                        "Drone photographer", "Aerial photographer", "Stock photographer",
                        "E-commerce photographer", "Photo retoucher", "Photo editor", "Photo restoration",
                        "Background removal", "Lightroom editor")));
        m.put("Music & audio", new Field(
                List.of("music", "musique", "musician", "musicien", "audio", "sound", "son", "voice", "voix",
                        "podcast", "beat", "dj"),
                List.of("Music", "Musician", "Music producer", "Beatmaker", "Composer", "Film composer",
                        "Game composer", "Songwriter", "Lyricist", "Singer", "Vocalist", "Rapper", "DJ",
                        "Session musician", "Guitarist", "Pianist", "Drummer", "Bassist", "Violinist",
                        "Mixing engineer", "Mastering engineer", "Recording engineer", "Sound designer",
                        "Audio engineer", "Audio editor", "Foley artist", "Jingle composer",
                        "Voice-over artist", "Voice actor", "Narrator", "Audiobook narrator",
                        "Podcast producer", "Podcast editor", "Podcast host", "Radio host",
                        "Music teacher", "Music arranger", "Orchestrator", "Live sound engineer")));
        m.put("Writing & content", new Field(
                List.of("writing", "writer", "redaction", "redacteur", "ecriture", "content", "contenu",
                        "copy", "blog", "author", "auteur", "editing"),
                List.of("Writing", "Copywriter", "Content writer", "SEO writer", "Blog writer",
                        "Technical writer", "Ghostwriter", "Author", "Novelist", "Poet", "Journalist",
                        "Editor", "Proofreader", "Copy editor", "Script writer", "Speechwriter",
                        "Grant writer", "Resume writer", "Cover letter writer", "Product description writer",
                        "Email copywriter", "UX copywriter", "Newsletter writer", "Social media copywriter",
                        "Academic writer", "Research writer", "Medical writer", "Legal writer",
                        "Financial writer", "Children's book author", "Content strategist",
                        "Content creator", "Storyteller", "Fact-checker", "Transcriptionist")));
        m.put("Translation & languages", new Field(
                List.of("translation", "traduction", "translator", "traducteur", "language", "langue",
                        "interpreter", "interprete", "localization"),
                List.of("Translator", "English–French translator", "French–English translator",
                        "Spanish translator", "German translator", "Arabic translator", "Chinese translator",
                        "Malagasy translator", "Portuguese translator", "Italian translator",
                        "Japanese translator", "Interpreter", "Sign language interpreter",
                        "Localization specialist", "Subtitle translator", "Language teacher",
                        "English teacher", "French teacher")));
        m.put("Marketing & growth", new Field(
                List.of("marketing", "marketer", "growth", "seo", "ads", "advertising", "publicite", "social media",
                        "reseaux sociaux", "community", "communication", "pr"),
                List.of("Marketing", "Digital marketer", "Marketing strategist", "Growth marketer",
                        "Growth hacker", "Performance marketer", "SEO specialist", "SEO consultant",
                        "SEM specialist", "Google Ads specialist", "Meta Ads specialist", "TikTok Ads specialist",
                        "LinkedIn Ads specialist", "PPC specialist", "Social media manager",
                        "Community manager", "Influencer marketer", "Influencer", "Content marketer",
                        "Email marketer", "Marketing automation specialist", "CRM specialist",
                        "HubSpot specialist", "Affiliate marketer", "Conversion rate optimizer",
                        "Marketing analyst", "Market researcher", "Brand ambassador", "Public relations",
                        "Communications manager", "Event marketer", "Product marketer",
                        "E-commerce marketer", "Amazon marketing specialist", "App store optimization",
                        "Local SEO specialist", "Video marketer", "Podcast marketer",
                        "Lead generation specialist", "Cold email specialist", "Copywriting strategist")));
        m.put("Sales & customer", new Field(
                List.of("sales", "vente", "commercial", "customer", "client", "support", "service client"),
                List.of("Sales representative", "Business developer", "Account manager",
                        "Sales consultant", "Appointment setter", "Telemarketer", "Customer success manager",
                        "Customer support", "Virtual assistant", "Executive assistant", "Personal assistant",
                        "Administrative assistant", "Office manager", "Receptionist", "Chat support agent",
                        "Call center agent", "Order fulfillment", "Dropshipping specialist",
                        "E-commerce manager", "Shopify store manager", "Amazon seller consultant",
                        "Etsy shop manager", "Marketplace manager")));
        m.put("Business, finance & legal", new Field(
                List.of("business", "finance", "accounting", "comptabilite", "comptable", "legal", "juridique",
                        "droit", "law", "consulting", "conseil", "hr", "rh", "entrepreneur"),
                List.of("Business consultant", "Management consultant", "Strategy consultant",
                        "Entrepreneur", "Startup advisor", "Business plan writer", "Financial analyst",
                        "Financial advisor", "Accountant", "Bookkeeper", "Tax consultant", "Auditor",
                        "Payroll specialist", "Controller", "CFO as a service", "Fundraising consultant",
                        "Investor relations", "Pitch deck consultant", "Lawyer", "Legal consultant",
                        "Paralegal", "Contract drafter", "Compliance officer", "Intellectual property consultant",
                        "HR consultant", "Recruiter", "Talent acquisition specialist", "Career coach",
                        "Operations manager", "Supply chain consultant", "Logistics coordinator",
                        "Procurement specialist", "Real estate agent", "Insurance advisor",
                        "Notary", "Legal translator")));
        m.put("Architecture & engineering", new Field(
                List.of("architecture", "architect", "architecte", "interior", "interieur", "engineering",
                        "ingenierie", "cad", "civil", "mechanical", "mecanique", "electrical"),
                List.of("Architect", "Interior designer", "Interior architect", "Landscape architect",
                        "Urban planner", "Home stager", "Kitchen designer", "Lighting designer",
                        "CAD designer", "AutoCAD drafter", "Revit modeler", "BIM specialist",
                        "SketchUp modeler", "Civil engineer", "Structural engineer", "Mechanical engineer",
                        "Electrical engineer", "Electronics engineer", "PCB designer", "Industrial designer",
                        "Physical product designer", "Furniture designer", "Mechatronics engineer",
                        "Chemical engineer", "Environmental engineer", "Energy consultant",
                        "Solar energy engineer", "Surveyor", "Quantity surveyor", "Construction manager")));
        m.put("Fashion & beauty", new Field(
                List.of("fashion", "mode", "beauty", "beaute", "style", "couture", "textile", "jewelry", "bijou"),
                List.of("Fashion designer", "Fashion stylist", "Personal stylist", "Textile designer",
                        "Pattern maker", "Tailor", "Seamstress", "Jewelry designer", "Accessories designer",
                        "Shoe designer", "Fashion illustrator", "Model", "Hair stylist", "Barber",
                        "Nail artist", "Beauty consultant", "Image consultant")));
        m.put("Crafts & making", new Field(
                List.of("craft", "artisan", "artisanat", "handmade", "maker", "fait main"),
                List.of("Artisan", "Woodworker", "Carpenter", "Ceramicist", "Potter", "Glass artist",
                        "Leather craftsman", "Embroiderer", "Knitter", "Candle maker", "Soap maker",
                        "Florist", "Floral designer", "Upholsterer", "Blacksmith", "Laser cutting designer",
                        "Cricut designer", "Model maker", "Prop maker", "Cosplay maker")));
        m.put("Education & coaching", new Field(
                List.of("education", "teacher", "professeur", "enseignant", "tutor", "tuteur", "coach",
                        "formation", "training", "trainer", "formateur", "course", "cours"),
                List.of("Teacher", "Tutor", "Online tutor", "Math tutor", "Science tutor",
                        "Programming instructor", "Coding bootcamp mentor", "Course creator",
                        "Instructional designer", "E-learning developer", "Corporate trainer",
                        "Public speaking coach", "Life coach", "Business coach", "Executive coach",
                        "Mindset coach", "Productivity coach", "Mentor", "Academic advisor",
                        "Exam preparation tutor", "Music instructor", "Art teacher", "Dance teacher",
                        "Yoga instructor", "Fitness coach", "Personal trainer", "Nutrition coach",
                        "Chess coach", "Driving instructor")));
        m.put("Health & wellness", new Field(
                List.of("health", "sante", "wellness", "bien etre", "medical", "medecin", "therapy", "therapie",
                        "fitness", "sport", "nutrition"),
                List.of("Nutritionist", "Dietitian", "Psychologist", "Therapist", "Counselor",
                        "Wellness coach", "Massage therapist", "Physiotherapist", "Meditation teacher",
                        "Health writer", "Medical transcriptionist", "Medical coder", "Pharmacist consultant",
                        "Doctor", "Nurse", "Sports coach", "Athlete", "Esports player", "Esports coach")));
        m.put("Events, food & lifestyle", new Field(
                List.of("event", "evenement", "food", "cuisine", "chef", "travel", "voyage", "tourism",
                        "tourisme", "lifestyle", "wedding", "mariage"),
                List.of("Event planner", "Wedding planner", "Event coordinator", "MC / host",
                        "Chef", "Private chef", "Pastry chef", "Baker", "Caterer", "Recipe developer",
                        "Food stylist", "Bartender", "Sommelier", "Travel planner", "Tour guide",
                        "Travel blogger", "Lifestyle blogger", "Food blogger", "Vlogger",
                        "Streamer", "Twitch streamer", "YouTuber", "Podcaster",
                        "Pet photographer", "Dog trainer", "Gardener", "Landscaper")));
        m.put("Science & research", new Field(
                List.of("science", "research", "recherche", "lab", "laboratoire", "academic", "academique"),
                List.of("Researcher", "Scientist", "Biologist", "Chemist", "Physicist", "Mathematician",
                        "Economist", "Sociologist", "Historian", "Archaeologist", "Environmental scientist",
                        "Agronomist", "Lab technician", "Clinical researcher", "UX research consultant",
                        "Survey designer", "Academic editor", "Thesis advisor")));
        return m;
    }
}
