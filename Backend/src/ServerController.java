import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * This class provides the datafields and methods to get commands from the client
 * and communicate the infromation back to the client
 * 
 * @author Anita Rezaee, Nolan Chan, Ben nielsen
 *
 */
public class ServerController implements Runnable{

	/**
	 * a DataBaseController object used for getting information from the database
	 */
	private DataBaseController database;
	/**
	 * a socket to allow communication between server and client
	 */
	private Socket aSocket;
	/**
	 * an ObjectInputStream object used for deserializing objects
	 */
	private ObjectInputStream objectIn;
	/**
	 * an ObjectInputStream object used for serializing objects
	 */
	private ObjectOutputStream objectOut;
	/**
	 * this object is used for writing to the socket
	 */
	private PrintWriter socketOut;
	/**
	 * this object is used for reading from the socket
	 */
	private BufferedReader socketIn;
	
	/**
	 * constructs a serverController object
	 * @param s socket used for communicating with the client
	 * @param db DataBaseController object used for communication with the database
	 */
	public ServerController(Socket s, DataBaseController db) {
		aSocket=s;
		database=db;
		
		try {
			socketIn= new BufferedReader (new InputStreamReader (aSocket.getInputStream()));
			socketOut= new PrintWriter ((aSocket.getOutputStream()));
			objectIn = new ObjectInputStream(aSocket.getInputStream());
			objectOut = new ObjectOutputStream(aSocket.getOutputStream());
		} catch (IOException e) {
			
			e.printStackTrace();
		}
	}
	
	/**
	 * each thread will run this method to process client's commands
	 * in each case of the switch case statement the server will receive a 
	 * comman string from the client and access the database to send results back 
	 * to the client.
	 */
	@Override
	public void run() {
		String line="";
		while(true) {
			try {
				line=socketIn.readLine();
				switch(line) {
					
				case "authenticate":
					validateLogin();
					break;
					
				case "getAllCourses":
					showCatalog();
					break;
					
				case "register": 
					int sectionId = Integer.parseInt(socketIn.readLine());
					int studentId = Integer.parseInt(socketIn.readLine());
					addCourse(sectionId, studentId);
					break;
					
				case "removeRegistration":
					int registrationId = Integer.parseInt(socketIn.readLine());
					int secId = Integer.parseInt(socketIn.readLine());
					dropCourse(registrationId, secId);
					break;
					
				case "getRegisteredCourses":
					int studentID= Integer.parseInt(socketIn.readLine());
					showRegistration(studentID);
					break;
					
				case "search":
					String courseName=socketIn.readLine();
					courseSearch(courseName);
					break;
					
				case "quit": 
					socketOut.println("Program is ending");
					break;
				}
			}catch (IOException e) {
				
				e.printStackTrace();
			}
		}
		
	}
	
	/**
	 * recieves an Student object from the client with the student's username and password
	 * the DataBaseController method is used to check if the student's credentials 
	 * can be found on the student table and therefore valid
	 * a Student object will be sent back to the client if the login credentials are valid 
	 */
	private void validateLogin() {
		Student student = null;
		try {
			student = (Student) objectIn.readObject();
		} catch (ClassNotFoundException | IOException e) {
			e.printStackTrace();
		}
		ResultSet result =database.validateLogin(student.getName(), student.getPassword());
		if(result!=null) {
			
			try {
				student= new Student(result.getString("name"), result.getInt(1), result.getString("pass"));
			
			} catch (SQLException e) {
				e.printStackTrace();
			}	
		}
		
		else {
			student=null;
		}
		try {
			
			objectOut.writeObject(student);
			
			} catch (IOException e) {
				e.printStackTrace();
			}
	
	}
	
	/**
	 * serializes a Catalog object that has a list of courses 
	 */
	private void showCatalog() {
		ResultSet res= database.showCatalog();
		CourseCatalog catalog = new CourseCatalog();
		Course currentCourse=null;
		
		try {
			while(res.next()) {
				
				if(currentCourse==null) {
					currentCourse = new Course(res.getString("courseName"), res.getInt(1));
				}
				
				else if(currentCourse.getCourseName() != res.getString("courseName")) {
					catalog.addCourse(currentCourse);
					currentCourse = new Course(res.getString("courseName"), res.getInt(1));
				}
				
				CourseOffering section = new CourseOffering(res.getInt(3), res.getInt(4), res.getInt(5));
				currentCourse.addSection(section);
			}
			catalog.addCourse(currentCourse);
		
			objectOut.writeObject(catalog);
		} catch (SQLException | IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * adds a course to registration table
	 * @param sectionId the id of the section that the student is registering in 
	 * @param studentId the id of the student registering 
	 */
	private void addCourse(int sectionId, int studentId) {
		boolean courseRegistered= database.addToRegistration(sectionId, studentId);
		if(courseRegistered==true) {
			socketOut.println("Course has been added to registration list");
		}
		else {
			socketOut.println("Registration Failed");
		}
	}
	
	/**
	 * delets a course from registration table
	 * @param registrationId the id of the registration that will be deleted from 
	 * registration table
	 * @param secId the section that is being deleted from the registration table
	 */
	private void dropCourse(int registrationId, int secId) {
		boolean courseRemoved= database.deleteFromRegistration(registrationId, secId);	
		if(courseRemoved==true) {
			socketOut.println("Course has been removed from the registration list");
		}
		else {
			socketOut.println("Course Removal failed");
		}
	}
	
	/**
	 * shows all the registration of the student
	 * will receive a studentID
	 * will use the student Id to look for the student's registration
	 * sends out a list of registration
	 */
	private void showRegistration(int studentID) {
		ResultSet result= database.showRegistration(studentID);
		ArrayList<Registration> registration= new ArrayList<Registration>();
		try {
			while(result.next()) {
				registration.add(new Registration(result.getInt(1), result.getInt(2), result.getInt(3), 
						result.getInt(4), result.getInt(5), result.getString(6)));
			}
			objectOut.writeObject(registration);
		} catch (SQLException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * finds the course using the course name on the course table
	 * @param courseName name of the course used to find the course on the course table
	 */
	private void courseSearch(String courseName) {
		ResultSet result = database.searchCourse(courseName);
		Course course=null;
		try {
				course = new Course(result.getString("courseName"), result.getInt(1));
				objectOut.writeObject(course);
		} catch (SQLException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
