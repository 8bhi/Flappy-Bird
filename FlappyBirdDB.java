import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public class FlappyBirdDB extends JPanel implements ActionListener, KeyListener {

    // --- Game Constants ---
    private int frameWidth = 400;
    private int frameHeight = 600;

    private int birdX = 100;
    private int birdY = 250;
    private int birdWidth = 20;
    private int birdHeight = 20;
    private double velocityY = 0;
    private double gravity = 0.25;
    private double jumpStrength = -5; // CHANGED: Reduced from -6 to -5 for lower jump height
    private int pipeWidth = 60;
    private int pipeGap = 120;
    private int pipeSpeed = 2;

    private List<Rectangle> pipes;
    private Timer timer;
    private boolean gameOver = false;
    private boolean started = false;
    private int score = 0;

    private JButton startButton, restartButton;
    private String username;

    // --- High Score and Restart State Variables ---
    // restartCount goes from 1 (first game) to 5 (final game)
    private int restartCount = 0; 
    private static final int MAX_RESTARTS = 5; 
    private String highScores = ""; 
    
    // NEW: To track scores within the 5-attempt session
    private List<Integer> sessionScores = new ArrayList<>(); 

    // --- Database Variables ---
    private Connection conn;

    public FlappyBirdDB() {
        JFrame frame = new JFrame("Flappy Bird with DB");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(frameWidth, frameHeight);
        frame.setResizable(false);
        frame.add(this);
        frame.setVisible(true);

        pipes = new ArrayList<>();
        timer = new Timer(20, this); 

        this.setFocusable(true);
        this.addKeyListener(this);
        this.setLayout(null);

        // Start Button
        startButton = new JButton("Start Game");
        startButton.setBounds(frameWidth / 2 - 60, frameHeight / 2 - 25, 120, 40);
        startButton.addActionListener(e -> promptUsername());
        this.add(startButton);

        // Restart Button
        restartButton = new JButton("Restart");
        restartButton.setBounds(frameWidth / 2 - 50, frameHeight / 2 + 180, 100, 35);
        restartButton.addActionListener(e -> startGame());
        restartButton.setVisible(false);
        this.add(restartButton);

        connectDatabase();
    }

    // --- Database and User Identity Methods ---

    private void connectDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            // NOTE: Replace "your_password" with your actual MySQL password
            conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/flappydb", "root", "1234");
            System.out.println("✅ Connected to database!");
        } catch (Exception e) {
            System.out.println("❌ Database connection failed: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Database connection failed. Scores and rankings will not be saved/loaded.", "DB Error", JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Checks if a username already exists in the database.
     */
    private boolean checkUsernameExists(String name) {
        if (conn == null) return false;
        try {
            PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM score WHERE username = ?");
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                rs.close();
                stmt.close();
                return true; 
            }
            rs.close();
            stmt.close();
        } catch (Exception e) {
            System.out.println("❌ Error checking username existence: " + e.getMessage());
        }
        return false;
    }

    private void promptUsername() {
        boolean unique = false;
        String tempUsername = null;
        
        while (!unique) {
            tempUsername = JOptionPane.showInputDialog(this, "Enter a unique username:");
            if (tempUsername == null) return; 
            tempUsername = tempUsername.trim();

            if (tempUsername.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                continue;
            }

            if (checkUsernameExists(tempUsername)) {
                JOptionPane.showMessageDialog(this, 
                    "Username '" + tempUsername + "' already exists in the ranking. Please choose another one.", 
                    "Username Taken", JOptionPane.WARNING_MESSAGE);
            } else {
                unique = true;
            }
        }
        
        username = tempUsername;
        restartCount = 0; 
        sessionScores.clear(); 
        startGame();
    }

    /**
     * Finds the highest score from the current session (up to 5 attempts) and saves it to the DB.
     * This is only called after the final (5th) attempt.
     */
    private void saveMaxSessionScoreToDB() {
        if (conn == null || username == null || sessionScores.isEmpty()) return;
        
        OptionalInt maxScore = sessionScores.stream().mapToInt(v -> v).max();
        if (!maxScore.isPresent()) return;
        
        int finalScore = maxScore.getAsInt();

        try {
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO score(username, score) VALUES(?, ?)");
            stmt.setString(1, username);
            stmt.setInt(2, finalScore);
            stmt.executeUpdate();
            stmt.close();
            System.out.println("✅ Final best session score saved for user: " + username + " with score: " + finalScore);

        } catch (Exception e) {
            System.out.println("❌ Error saving max session score: " + e.getMessage());
        }
    }

    /**
     * Fetches the top 3 scores from the database and formats them for display.
     */
    private void fetchTopScores() {
        if (conn == null) {
            highScores = "DB connection failed. Can't retrieve rankings.";
            return;
        }
        StringBuilder sb = new StringBuilder("--- Top 3 High Scores ---\n");
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT username, score FROM score ORDER BY score DESC LIMIT 3");
            
            int rank = 1;
            while (rs.next()) {
                sb.append(String.format("#%d: %s (%d)\n", rank++, rs.getString("username"), rs.getInt("score")));
            }
            
            rs.close();
            stmt.close();
            
            highScores = sb.toString();

        } catch (Exception e) {
            System.out.println("❌ Error fetching scores: " + e.getMessage());
            highScores = "Error fetching rankings.";
        }
    }
    
    // --- Game Logic Methods ---

    private void startGame() {
        if (username == null || username.isEmpty()) {
            promptUsername(); 
            return;
        }
        
        if (restartCount >= MAX_RESTARTS) {
            timer.stop();
            JOptionPane.showMessageDialog(this, 
                "You have used all " + MAX_RESTARTS + " attempts for user '" + username + "'. " +
                "Your best score has been saved to the ranking system. Please enter a new username to play again.", 
                "Session Ended", JOptionPane.INFORMATION_MESSAGE);
            
            restartButton.setVisible(false); 
            return;
        }

        restartCount++; 
        
        startButton.setVisible(false);
        restartButton.setVisible(false);
        started = true;
        gameOver = false;
        
        birdY = 250;
        velocityY = 0;
        score = 0;
        pipes.clear();
        addPipe(); 
        timer.start();
        this.requestFocusInWindow(); 
    }

    private void addPipe() {
        int minHeight = 50;
        int maxHeight = frameHeight - pipeGap - minHeight - 50; 
        int height = (int) (Math.random() * (maxHeight - minHeight) + minHeight);

        pipes.add(new Rectangle(frameWidth, 0, pipeWidth, height));
        pipes.add(new Rectangle(frameWidth, height + pipeGap, pipeWidth, frameHeight - height - pipeGap));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameOver || !started) return;

        velocityY += gravity;
        birdY += velocityY;

        ArrayList<Rectangle> pipesToRemove = new ArrayList<>();
        boolean scoredThisFrame = false;

        for (Rectangle pipe : pipes) {
            pipe.x -= pipeSpeed;

            if (pipe.x + pipe.width < birdX && pipe.y == 0 && !scoredThisFrame) {
                score++;
                scoredThisFrame = true; 
            }

            if (pipe.x + pipe.width < 0) {
                pipesToRemove.add(pipe);
            }
        }

        pipes.removeAll(pipesToRemove); 

        if (pipes.isEmpty() || pipes.get(pipes.size() - 2).x < frameWidth - 200) {
            addPipe();
        }

        Rectangle birdBounds = new Rectangle(birdX, birdY, birdWidth, birdHeight);
        boolean crashed = false;

        for (Rectangle pipe : pipes) {
            if (birdBounds.intersects(pipe)) {
                crashed = true;
                break;
            }
        }

        if (birdY > frameHeight - birdHeight || birdY < 0) {
            crashed = true;
        }

        if (crashed) {
            gameOver = true;
            timer.stop();
            
            sessionScores.add(score);
            System.out.println("Attempt " + restartCount + " score logged: " + score);

            if (restartCount == MAX_RESTARTS) {
                saveMaxSessionScoreToDB(); 
            } else {
                restartButton.setVisible(true);
            }

            fetchTopScores(); 
        }

        repaint();
    }

    // --- Drawing/Rendering ---

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.setColor(new Color(135, 206, 250)); 
        g.fillRect(0, 0, frameWidth, frameHeight);

        g.setColor(Color.RED);
        g.fillOval(birdX, birdY, birdWidth, birdHeight);

        g.setColor(Color.GREEN.darker().darker()); 
        for (Rectangle pipe : pipes) g.fillRect(pipe.x, pipe.y, pipe.width, pipe.height);

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString("Score: " + score, 20, 40);
        
        if (started && username != null) {
            g.setFont(new Font("Arial", Font.PLAIN, 16));
            g.setColor(Color.BLACK);
            int displayAttempts = gameOver ? restartCount : restartCount;
            g.drawString("Attempt: " + displayAttempts + " / " + MAX_RESTARTS, frameWidth - 140, 40);
            g.drawString("User: " + username, frameWidth - 140, 65);
        }

        if (!started) {
            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial", Font.BOLD, 30));
            g.drawString("Press Start", frameWidth / 2 - 80, frameHeight / 2 - 50);
        } else if (gameOver) {
            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial", Font.BOLD, 36));
            g.drawString("GAME OVER!", frameWidth / 2 - 115, 150);
            
            g.setFont(new Font("Arial", Font.BOLD, 24));
            g.drawString("Last Score: " + score, frameWidth / 2 - 70, 200);

            OptionalInt maxScore = sessionScores.stream().mapToInt(v -> v).max();
            if (maxScore.isPresent()) {
                g.setFont(new Font("Arial", Font.BOLD, 20));
                g.drawString("Session Best: " + maxScore.getAsInt(), frameWidth / 2 - 80, 230);
            }
            
            // --- Transparent Background for High Scores ---
            int bgWidth = frameWidth - 80; // Example width
            int bgHeight = 150; // Example height
            int bgX = (frameWidth - bgWidth) / 2;
            int bgY = 250; 

            g.setColor(new Color(0, 0, 0, 150)); // Black with 150 alpha (translucency)
            g.fillRoundRect(bgX, bgY, bgWidth, bgHeight, 20, 20); // Rounded rectangle

            // --- Display High Scores (Bold Font) ---
            g.setColor(Color.WHITE); // Text color for high scores
            g.setFont(new Font("Monospaced", Font.BOLD, 20)); // Set font to BOLD
            
            String[] lines = highScores.split("\n");
            int yOffset = bgY + 30; // Start drawing text inside the background rectangle
            for (String line : lines) {
                FontMetrics fm = g.getFontMetrics();
                int x = (frameWidth - fm.stringWidth(line)) / 2;
                g.drawString(line, x, yOffset);
                yOffset += 25; 
            }
        }
    }

    // --- Key Listener ---

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();

        if (code == KeyEvent.VK_SPACE && started && !gameOver) {
            velocityY = jumpStrength;
        }
        if (code == KeyEvent.VK_SPACE && gameOver) {
            startGame();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {}
    @Override
    public void keyTyped(KeyEvent e) {}

    // --- Main Method ---

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FlappyBirdDB());
    }
}