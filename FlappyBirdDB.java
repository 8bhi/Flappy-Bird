import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.sql.*;
import java.util.ArrayList;

public class FlappyBirdDB extends JPanel implements ActionListener, KeyListener {

    // --- Game Constants (UPDATED) ---
    private int frameWidth = 400;
    private int frameHeight = 600;

    private int birdX = 100;
    private int birdY = 250;
    private int birdWidth = 20;     // ✅ UPDATED: Bird size is 20px
    private int birdHeight = 20;    // ✅ UPDATED: Bird size is 20px
    private double velocityY = 0;
    private double gravity = 0.25;
    private double jumpStrength = -6;

    private int pipeWidth = 60;
    private int pipeGap = 120;       //
    private int pipeSpeed = 2;

    private ArrayList<Rectangle> pipes;
    private Timer timer;
    private boolean gameOver = false;
    private boolean started = false;
    private int score = 0;

    private JButton startButton, restartButton;
    private String username;

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
        timer = new Timer(20, this); // Game loop runs every 20ms

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
        restartButton.setBounds(frameWidth / 2 - 50, frameHeight / 2 + 40, 100, 35);
        restartButton.addActionListener(e -> startGame());
        restartButton.setVisible(false);
        this.add(restartButton);

        connectDatabase();
    }

    // --- Database Methods ---

    private void promptUsername() {
        // Use a persistent dialog to ensure a non-empty username is entered
        while (username == null || username.trim().isEmpty()) {
            username = JOptionPane.showInputDialog(this, "Enter username:");
            if (username == null) return; // User cancelled
            username = username.trim();
        }
        startGame();
    }

    private void connectDatabase() {
        try {
            // Load the MySQL JDBC Driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            // NOTE: Replace "your_password" with your actual MySQL password
            // Example for port 3307
            conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/flappydb", "root", "1234");
            System.out.println("✅ Connected to database!");
        } catch (Exception e) {
            System.out.println("❌ Database connection failed: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Database connection failed. Scores will not be saved.", "DB Error", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void saveScoreToDB() {
        if (conn == null || username == null) return;
        try {
            // Use a PreparedStatement to prevent SQL injection
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO score(username, score) VALUES(?, ?)");
            stmt.setString(1, username);
            stmt.setInt(2, score);
            stmt.executeUpdate();
            stmt.close();
            System.out.println("✅ Score saved for user: " + username + " with score: " + score);
        } catch (Exception e) {
            System.out.println("❌ Error saving score: " + e.getMessage());
        }
    }

    // --- Game Logic Methods ---

    private void startGame() {
        startButton.setVisible(false);
        restartButton.setVisible(false);
        started = true;
        gameOver = false;
        // Reset bird and game state
        birdY = 250;
        velocityY = 0;
        score = 0;
        pipes.clear();
        addPipe(); // Initial pipe
        timer.start();
        this.requestFocusInWindow(); // Ensure the panel has focus for KeyListener
    }

    private void addPipe() {
        // Calculate a safe random height for the top pipe
        // Ensures there's enough space for the top pipe, the gap, and the bottom pipe
        int minHeight = 50;
        int maxHeight = frameHeight - pipeGap - minHeight - 50; // 50 is buffer
        int height = (int) (Math.random() * (maxHeight - minHeight) + minHeight);

        // Top Pipe
        pipes.add(new Rectangle(frameWidth, 0, pipeWidth, height));
        // Bottom Pipe
        pipes.add(new Rectangle(frameWidth, height + pipeGap, pipeWidth, frameHeight - height - pipeGap));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameOver || !started) return;

        // 1. Bird Movement
        velocityY += gravity;
        birdY += velocityY;

        // 2. Pipe Movement and Score Update
        ArrayList<Rectangle> pipesToRemove = new ArrayList<>();
        boolean scoredThisFrame = false;

        for (Rectangle pipe : pipes) {
            pipe.x -= pipeSpeed;

            // Check if pipe has passed the bird for scoring
            if (pipe.x + pipe.width < birdX && pipe.y == 0 && !scoredThisFrame) {
                // Only count the top pipe to prevent double-counting
                score++;
                scoredThisFrame = true; // Prevents scoring multiple times per pipe set per frame
            }

            // Mark pipe for removal if it's off-screen
            if (pipe.x + pipe.width < 0) {
                pipesToRemove.add(pipe);
            }
        }

        pipes.removeAll(pipesToRemove); // Remove pipes off-screen

        // Add a new set of pipes when the last one is far enough away
        // Check the x-coordinate of the last added pipe (the one with the largest x)
        if (pipes.isEmpty() || pipes.get(pipes.size() - 2).x < frameWidth - 200) {
            addPipe();
        }


        // 3. Check Collision
        Rectangle birdBounds = new Rectangle(birdX, birdY, birdWidth, birdHeight);

        // Collision with pipes
        for (Rectangle pipe : pipes) {
            if (birdBounds.intersects(pipe)) {
                gameOver = true;
                timer.stop();
                saveScoreToDB();
                restartButton.setVisible(true);
            }
        }

        // Collision with ground or ceiling
        if (birdY > frameHeight - birdHeight || birdY < 0) {
            gameOver = true;
            timer.stop();
            saveScoreToDB();
            restartButton.setVisible(true);
        }

        repaint();
    }

    // --- Drawing/Rendering ---

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Background (Sky)
        g.setColor(new Color(135, 206, 250)); // Light Blue
        g.fillRect(0, 0, frameWidth, frameHeight);

        // Bird
        g.setColor(Color.RED);
        g.fillOval(birdX, birdY, birdWidth, birdHeight);

        // Pipes
        g.setColor(Color.GREEN.darker().darker()); // Darker Green
        for (Rectangle pipe : pipes) g.fillRect(pipe.x, pipe.y, pipe.width, pipe.height);

        // Score
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString("Score: " + score, 20, 40);

        // Game State Messages
        if (!started) {
            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial", Font.BOLD, 30));
            g.drawString("Press Start", frameWidth / 2 - 80, frameHeight / 2 - 50);
        } else if (gameOver) {
            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial", Font.BOLD, 30));
            g.drawString("Game Over!", frameWidth / 2 - 90, frameHeight / 2 - 30);
            g.setFont(new Font("Arial", Font.PLAIN, 20));
            g.drawString("Final Score: " + score, frameWidth / 2 - 70, frameHeight / 2 + 10);
        }
    }

    // --- Key Listener ---

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();

        // Flap the bird on SPACE
        if (code == KeyEvent.VK_SPACE && started && !gameOver) {
            velocityY = jumpStrength;
        }
        // Allows restarting the game using SPACE after Game Over
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
        // Run the game on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> new FlappyBirdDB());
    }
}