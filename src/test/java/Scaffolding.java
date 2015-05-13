import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.io.Resources;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <p>Utility class to generate code from templates using configuration specified in <code>scaffolding.json</code></p>
 * <p>Use this to rapidly create all the supporting code specific to your project for a particular purpose.</p>
 * <p>Examples of use are:</p>
 * <ul>
 * <li>Entities and their supporting structures (DAOs, repositories, services, resources, unit tests, models and so on</li>
 * <li>Rapid generation of common design patterns</li>
 * </ul>
 * <p>There is no mandated scaffold template - you can use anything you like. It just has to have placeholders defined using
 * the Handlebars notation with no spaces (e.g. <code>{{package}}</code>) listed below:</p>
 * <ul>
 * <li><code>package</code>: Base package, e.g. <code>org.example</code></li>
 * <li><code>entity-class</code>: Entity class, e.g. <code>AdminUser</code></li>
 * <li><code>entity-variable</code>: Entity variable, e.g. <code>adminUser</code></li>
 * <li><code>entity-snake</code>: Entity variable as snake case, e.g. <code>admin_user</code></li>
 * <li><code>entity-snake-upper</code>: Entity variable as uppercase snake case, e.g. <code>ADMIN_USER</code></li>
 * <li><code>entity-comment</code>: Entity variable as a comment, e.g. <code>admin user</code></li>
 * <li><code>entity-hyphen</code>: Entity variable in hyphenated form, e.g. <code>admin-user</code></li>
 * </ul>
 * <h3>How to install</h3>
 * <p>Just copy this source code into your project under <code>src/test/java</code>. You might want to copy in <code>scaffolding.json</code>
 * as well.</p>
 * <h3>Quickly generate templates from existing code</h3>
 * <p><code>Scaffolding</code> is specific to your application so you can read existing examples and turn them into
 * templates. After a bit of editing they will be suitable for use in your application (and others based on it). To get <code>Scaffolding</code>
 * to read your existing code you need to provide a <code>scaffolding.js</code> configuration like this:</p>
 * <pre>
 * {
 *   "base_package":"org.example.api",
 *   "read": true,
 *   "entities": ["MyEntity"]
 * }
 * </pre>
 * <p>All code from <code>base_package</code> and below will be recursively examined and templates built. These will be
 * stored in a directory structure under <code>src/test/resources/scaffolding</code>. You can then delete any that are
 * not useful and edit those that remain to meet your requirements. Usually there will be little to no editing
 * required.</p>
 * <h3>Generate code from templates</h3>
 * <p>Once you have your templates in place, you can use them to generate new code. This is again driven by the <code>scaffolding.json</code>
 * files. You switch away from <code>read</code> and provide a list of new entities that you would like created:</p>
 * <pre>
 * {
 *   "base_package":"org.example.api",
 *   "read": false,
 *   "entities": ["Role", "DataSource"]
 * }
 * </pre>
 * <p>Using the above, the generic templates built from the <code>MyEntity</code> will be used to produce the
 * equivalent for<code>Role</code> and <code>DataSource</code>. If you have been careful with what is included in
 * <code>User</code> then the produced code will act as good launch point for the new entities.</p>
 *
 * @author Gary Rowe (http://gary-rowe.com)
 * @since 1.5.0
 */
public class Scaffolding {

  // Base path locations
  private static final String SCAFFOLDING = "scaffolding/";
  private static final String BASE_TEMPLATE_PATH = "src/test/resources/" + SCAFFOLDING;
  private static final String BASE_SRC_PATH = "src";
  private static final String ROOT_PATH = ".";

  // File filters
  private static final String[] IGNORE_FILE_REGEXES = new String[]{
    "scaffolding.json$",
    ".*\\.classpath$",
    ".*\\.project$",
    ".*\\.wtpmodules$",
    ".*\\.iml$",
    ".*\\.iws$",
    ".*nb.*\\.xml$",
    ".*\\.DS_Store$",
    ".*\\.xdoclet$"
  };
  private static final Pattern[] ignoreFilePatterns = new Pattern[IGNORE_FILE_REGEXES.length];

  // Directives
  private static final String BASE_PACKAGE_DIRECTIVE = "{{package}}";
  private static final String BASE_PACKAGE_PATH_DIRECTIVE = "{{package-path}}";
  private static final String ENTITY_CLASS_DIRECTIVE = "{{entity-class}}";
  private static final String ENTITY_TITLE_DIRECTIVE = "{{entity-title}}";
  private static final String ENTITY_VARIABLE_DIRECTIVE = "{{entity-variable}}";
  private static final String ENTITY_HYPHEN_DIRECTIVE = "{{entity-hyphen}}";
  private static final String ENTITY_COMMENT_DIRECTIVE = "{{entity-comment}}";
  private static final String ENTITY_SNAKE_DIRECTIVE = "{{entity-snake}}";
  private static final String ENTITY_SNAKE_UPPER_DIRECTIVE = "{{entity-snake-upper}}";
  private static final String DIRECTIVE_REGEX = "\\{\\{entity.\\S+\\}\\}";
  private static final Pattern ENTITY_DIRECTIVE_PATTERN = Pattern.compile(DIRECTIVE_REGEX);

  /**
   * Main entry point to the scaffolding operations
   *
   * @param args [0]: path to <code>scaffolding.json</code>
   *
   * @throws java.io.IOException If something goes wrong
   */
  public static void main(String[] args) throws IOException {

    if (args == null || args.length != 1) {
      args = new String[]{"scaffolding.json"};
    }

    InputStream is = new FileInputStream(args[0]);
    ObjectMapper mapper = new ObjectMapper();

    // Pre-compile the "ignore file" patterns
    for (int i = 0; i < IGNORE_FILE_REGEXES.length; i++) {
      ignoreFilePatterns[i] = Pattern.compile(IGNORE_FILE_REGEXES[i]);
    }

    ScaffoldingConfiguration sc = mapper.readValue(is, Scaffolding.ScaffoldingConfiguration.class);

    new Scaffolding().run(sc);

  }

  /**
   * Executes the process
   *
   * @param sc The configuration
   *
   * @throws java.io.IOException If something goes wrong
   */
  private void run(ScaffoldingConfiguration sc) throws IOException {

    Preconditions.checkNotNull(sc);

    if (sc.isRead()) {

      System.out.println("Reading existing project and extracting templates");

      // Build URIs for all files within the project
      Set<URI> projectUris = Sets.newHashSet();
      recurseFiles(BASE_SRC_PATH, projectUris);
      sc.setProjectUris(projectUris);

      // Add root level files
      File[] files = new File(ROOT_PATH).listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isDirectory()) {
            continue;
          }

          String fileName = file.getName();

          // Handle common exclusions
          boolean ignore = false;
          for (Pattern pattern : ignoreFilePatterns) {
            if (pattern.matcher(fileName).matches()) {
              // It's on the ignore list
              ignore = true;
            }
          }

          if (!ignore) {
            projectUris.add(file.toURI());
          } else {
            System.err.println("Ignoring '" + fileName + "'");
          }
        }
      }

      readTemplates(sc);

    } else {

      System.out.println("Writing new code from templates");

      // Build URIs for all files within the project
      Set<URI> projectUris = Sets.newHashSet();
      recurseFiles(BASE_TEMPLATE_PATH + sc.getProfilePath(), projectUris);
      sc.setProjectUris(projectUris);

      writeTemplates(sc);
    }

  }

  /**
   * Scans the existing project and builds templates from the code
   *
   * @param sc The configuration
   *
   * @throws java.io.IOException If something goes wrong
   */
  private void readTemplates(ScaffoldingConfiguration sc) throws IOException {

    Set<String> entities = sc.getEntities();
    String basePackage = sc.getBasePackage();

    // Form a set of all classes in the classpath starting at the base package
    String workDir = (new File("")).toURI().toString();

    for (URI uri : sc.getProjectUris()) {

      String projectPath = uri.toString().replace(workDir, "");

      // Read the source file
      String sourceCode = Resources.toString(uri.toURL(), Charset.defaultCharset());

      // Multiple entities may lead to overlapping templates
      // but some project structures can cater for this
      for (String entity : entities) {

        // Work out the template target
        String templateTarget = BASE_TEMPLATE_PATH + sc.getProfilePath() + projectPath + ".hbs";

        // Introduce the base package directive
        String content = sourceCode.replace(basePackage, BASE_PACKAGE_DIRECTIVE);

        // Build the patterns to recognise the entities
        String entityVariable = entity.substring(0, 1).toLowerCase() + entity.substring(1);
        String entityTitle = toTitle(entityVariable);
        String entitySnake = toSnakeCase(entityVariable);
        String entityComment = toComment(entityVariable);
        String entityHyphen = toHyphen(entityVariable);

        // Check for entity content
        content = content
          .replace(entity, ENTITY_CLASS_DIRECTIVE)
            // Detect title case (e.g. in README)
          .replace(entityTitle, ENTITY_TITLE_DIRECTIVE)
            // Detect variable case (e.g. in methods)
          .replace(entityVariable, ENTITY_VARIABLE_DIRECTIVE)
            // Detect comment (e.g. in Javadocs)
          .replace(entityComment, ENTITY_COMMENT_DIRECTIVE)
            // Detect ADMIN_USER
          .replace(entitySnake.toUpperCase(), ENTITY_SNAKE_UPPER_DIRECTIVE)
            // Detect admin_user and "admin_user"
          .replace(entitySnake, ENTITY_SNAKE_DIRECTIVE)
            // Detect admin-user
          .replace(entityHyphen, ENTITY_HYPHEN_DIRECTIVE)
        ;

        templateTarget = templateTarget
          .replace(entity, ENTITY_CLASS_DIRECTIVE)
          .replace(entityTitle, ENTITY_TITLE_DIRECTIVE)
          .replace(entityVariable, ENTITY_VARIABLE_DIRECTIVE)
          .replace(entitySnake.toUpperCase(), ENTITY_SNAKE_UPPER_DIRECTIVE)
          .replace(entitySnake, ENTITY_SNAKE_DIRECTIVE)
          .replace(entityHyphen, ENTITY_HYPHEN_DIRECTIVE)
        ;

        // Check if path or content must contain directives
        if (sc.isOnlyWithEntityDirectives() && !containsEntityDirectives(projectPath, content)) {
          // Ignore
          System.err.println("Ignoring '" + projectPath + "' due to directive restriction.");
          continue;
        }

        // Write the content
        writeResult(content, templateTarget);
      }
    }
  }

  /**
   * @param path    The path
   * @param content The content
   *
   * @return True if either the path or content contain entity directives
   */
  private boolean containsEntityDirectives(String path, String content) {
    return ENTITY_DIRECTIVE_PATTERN.matcher(path).find() || ENTITY_DIRECTIVE_PATTERN.matcher(content).find();
  }

  /**
   * Handles the process of writing out the templates
   *
   * @param sc The configuration
   *
   * @throws java.io.IOException If something goes wrong
   */
  private void writeTemplates(ScaffoldingConfiguration sc) throws IOException {

    Set<String> entities = sc.getEntities();
    String basePackage = sc.getBasePackage();
    String outputDirectory = sc.getOutputDirectory();

    // Read all the templates
    Map<String, String> templateMap = buildTemplateMap(sc);

    // Work through the entities applying the templates
    for (String entity : entities) {

      String entityVariable = entity.substring(0, 1).toLowerCase() + entity.substring(1);
      String entityTitle = toTitle(entityVariable);
      String entitySnake = toSnakeCase(entityVariable);
      String entityHyphen = toHyphen(entityVariable);
      String entityComment = toComment(entityVariable);

      Map<String, String> directiveMap = Maps.newHashMap();
      directiveMap.put(BASE_PACKAGE_DIRECTIVE, basePackage);
      directiveMap.put(BASE_PACKAGE_PATH_DIRECTIVE, sc.getBasePath());
      directiveMap.put(ENTITY_CLASS_DIRECTIVE, entity);
      directiveMap.put(ENTITY_TITLE_DIRECTIVE, entityTitle);
      directiveMap.put(ENTITY_VARIABLE_DIRECTIVE, entityVariable);
      directiveMap.put(ENTITY_HYPHEN_DIRECTIVE, entityHyphen);
      directiveMap.put(ENTITY_COMMENT_DIRECTIVE, entityComment);
      directiveMap.put(ENTITY_SNAKE_UPPER_DIRECTIVE, entitySnake.toUpperCase());
      directiveMap.put(ENTITY_SNAKE_DIRECTIVE, entitySnake);

      for (Map.Entry<String, String> templateEntry : templateMap.entrySet()) {

        String content = templateEntry.getValue();

        // Transform the target
        String target = templateEntry.getKey();
        target = target.substring(0, target.length() - 4);

        // Check if path or content must contain directives
        if (sc.isOnlyWithEntityDirectives() && !containsEntityDirectives(target, content)) {
          // Ignore
          System.err.println("Ignoring '" + target + "' due to directive restriction.");
          continue;
        }

        // Transform target and content using directives
        for (Map.Entry<String, String> directiveEntry : directiveMap.entrySet()) {

          target = target.replace(directiveEntry.getKey(), directiveEntry.getValue());
          content = content.replace(directiveEntry.getKey(), directiveEntry.getValue());

        }

        // Write out the result
        writeResult(content, outputDirectory + "/" + target);
      }

    }

  }

  /**
   * @return A Map keyed on the target name and containing the template with directives
   *
   * @throws java.io.IOException If something goes wrong
   */
  private Map<String, String> buildTemplateMap(ScaffoldingConfiguration sc) throws IOException {

    // Provide a map for target and template
    Map<String, String> templateMap = Maps.newHashMap();

    // Form a set of all classes in the classpath starting at the base package
    String workDir = (new File("")).toURI().getPath();

    for (URI uri : sc.getProjectUris()) {

      String projectPath = uri.getPath().replace(workDir, "");

      // Filter out any non-scaffolding resources
      if (projectPath.contains(SCAFFOLDING) && projectPath.endsWith(".hbs")) {

        String target = projectPath.replace(BASE_TEMPLATE_PATH + sc.getProfilePath(), "");
        String template = Resources.toString(uri.toURL(), Charsets.UTF_8);

        templateMap.put(target, template);

      }

    }

    return templateMap;
  }

  /**
   * <p>Converts camel case to snake case as follows:</p>
   * <ul>
   * <li><code>This</code>: <code>this</code></li>
   * <li><code>ThisIs</code>: <code>this_is</code></li>
   * <li><code>thisIsATest</code>: <code>this_is_a_test</code></li>
   * <li><code>this1Is12A123Test1234</code>: <code>this_1_is_12_a_123_test_1234</code></li>
   * </ul>
   * <p>Adapted from <a href="http://stackoverflow.com/a/2560017/396747">Stack Overflow answer</a></p>
   *
   * @param camelCase The camel case (with arbitrary initial capitalisation)
   *
   * @return A snake case version in lowercase
   */
  private String toSnakeCase(String camelCase) {
    return camelCase.replaceAll(
      String.format("%s|%s|%s",
        "(?<=[A-Z])(?=[A-Z][a-z])",
        "(?<=[^A-Z])(?=[A-Z])",
        "(?<=[A-Za-z])(?=[^A-Za-z])"
      ),
      "_"
    ).toLowerCase();
  }

  /**
   * <p>Converts camel case to document lowercase as follows:</p>
   * <ul>
   * <li><code>This</code>: <code>this</code></li>
   * <li><code>ThisIs</code>: <code>this is</code></li>
   * <li><code>thisIsATest</code>: <code>this is a test</code></li>
   * <li><code>this1Is12A123Test1234</code>: <code>this 1 is 12 a 123 test 1234</code></li>
   * </ul>
   * <p>Adapted from <a href="http://stackoverflow.com/a/2560017/396747">Stack Overflow answer</a></p>
   *
   * @param camelCase The camel case (with arbitrary initial capitalisation)
   *
   * @return A document version in lowercase (useful for describing entities in Javadocs)
   */
  private String toComment(String camelCase) {
    return camelCase.replaceAll(
      String.format("%s|%s|%s",
        "(?<=[A-Z])(?=[A-Z][a-z])",
        "(?<=[^A-Z])(?=[A-Z])",
        "(?<=[A-Za-z])(?=[^A-Za-z])"
      ),
      " "
    ).toLowerCase();
  }

  /**
   * <p>Converts camel case to hyphenated lowercase as follows:</p>
   * <ul>
   * <li><code>This</code>: <code>this</code></li>
   * <li><code>ThisIs</code>: <code>this-is</code></li>
   * <li><code>thisIsATest</code>: <code>this-is-a-test</code></li>
   * <li><code>this1Is12A123Test1234</code>: <code>this-1-is-12-a-123-test-1234</code></li>
   * </ul>
   * <p>Adapted from <a href="http://stackoverflow.com/a/2560017/396747">Stack Overflow answer</a></p>
   *
   * @param camelCase The camel case (with arbitrary initial capitalisation)
   *
   * @return A hyphen version in lowercase (useful for describing entities in RESTful endpoints)
   */
  private String toHyphen(String camelCase) {
    return camelCase.replaceAll(
      String.format("%s|%s|%s",
        "(?<=[A-Z])(?=[A-Z][a-z])",
        "(?<=[^A-Z])(?=[A-Z])",
        "(?<=[A-Za-z])(?=[^A-Za-z])"
      ),
      "-"
    ).toLowerCase();
  }

  /**
   * <p>Converts camel case to title case with spaces as follows:</p>
   * <ul>
   * <li><code>This</code>: <code>This</code></li>
   * <li><code>ThisIs</code>: <code>This is</code></li>
   * <li><code>thisIsATest</code>: <code>This Is A Test</code></li>
   * <li><code>this1Is12A123Test1234</code>: <code>This 1 Is 12 A 123 Test 1234</code></li>
   * </ul>
   * <p>Adapted from <a href="http://stackoverflow.com/a/2560017/396747">Stack Overflow answer</a></p>
   *
   * @param camelCase The camel case (with arbitrary initial capitalisation)
   *
   * @return A document version in title case (useful for describing entities in titles)
   */
  private String toTitle(String camelCase) {
    String spaced = camelCase.replaceAll(
      String.format("%s|%s|%s",
        "(?<=[A-Z])(?=[A-Z][a-z])",
        "(?<=[^A-Z])(?=[A-Z])",
        "(?<=[A-Za-z])(?=[^A-Za-z])"
      ),
      " "
    );

    return spaced.substring(0, 1).toUpperCase() + spaced.substring(1);
  }

  /**
   * <p>Recursive method</p>
   *
   * @param directory   The starting directory
   * @param projectUris The set of URIs for all the files
   *
   * @return The set of all URIs
   *
   * @throws java.io.IOException If something goes wrong
   */
  private Set<URI> recurseFiles(String directory, Set<URI> projectUris) throws IOException {

    File[] files = new File(directory).listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory()) {
          recurseFiles(file.getAbsolutePath(), projectUris);
          continue;
        }
        if (file.getName().matches("^(.*?)")) {
          projectUris.add(file.toURI());
        }
      }
    }

    return projectUris;
  }

  /**
   * @param content  The content
   * @param fileName The file name
   *
   * @throws java.io.IOException If something goes wrong
   */
  private void writeResult(String content, String fileName) throws IOException {

    File file = new File(fileName);
    if (file.exists()) {
      System.err.println("Skipping '" + fileName + "' to prevent an overwrite.");
      return;
    }
    System.out.println("Writing: '" + fileName + "'");
    Files.createParentDirs(file);

    // Have a good target
    Writer writer = new FileWriter(file);
    writer.write(content);
    writer.close();
  }

  /**
   * <p>Configuration to use when reading or writing the templates.</p>
   */
  public static class ScaffoldingConfiguration {

    @JsonProperty("profile")
    private String profile = "";

    @JsonProperty("output_directory")
    private String outputDirectory = ".";

    @JsonProperty("base_package")
    private String basePackage = "org.example";

    @JsonProperty
    private boolean read = false;

    @JsonProperty("only_with_entity_directives")
    private boolean onlyWithEntityDirectives = false;

    @JsonProperty
    private Set<String> entities = Sets.newHashSet();

    @JsonIgnore
    private Set<URI> projectUris = Sets.newHashSet();

    public ScaffoldingConfiguration() {
    }

    /**
     * A profile name is appended as "src/test/resources/scaffolding"+profile to manage
     * collections of scaffolding templates targeting different variations
     *
     * For example, a profile of "dw-0.6.1-mongodb" could be used to indicate that these
     * template would create a Dropwizard v0.6.1 microservice containing suitable data access code
     * for MongoDB
     *
     * @return The name of the profile (e.g. "dw-0.6.1-mongodb")
     */
    public String getProfile() {
      return profile;
    }

    /**
     * @return The name of the profile with path separator appended (if not empty)
     */
    public String getProfilePath() {

      if (profile == null || profile.length()==0) {
        return "";
      }

      if (!profile.endsWith("/")) {
        return profile + "/";
      }

      return profile;
    }

    /**
     * @return The output directory when writing (e.g. ".", "target/generated-sources")
     */
    public String getOutputDirectory() {
      return outputDirectory;
    }

    /**
     * @return The base package to read from/write to
     */
    public String getBasePackage() {
      return basePackage;
    }

    /**
     * @return True if Scaffolding should scan the project looking for templates
     */
    public boolean isRead() {
      return read;
    }

    /**
     * @return True if Scaffolding should only store/use templates that contain entity directives in path or content
     */
    public boolean isOnlyWithEntityDirectives() {
      return onlyWithEntityDirectives;
    }

    public Set<String> getEntities() {
      return entities;
    }

    /**
     * @return The base package in path format
     */
    @JsonIgnore
    public String getBasePath() {
      return basePackage.replace(".", "/");
    }

    public void setProjectUris(Set<URI> projectUris) {
      this.projectUris = projectUris;
    }

    /**
     * @return The URIs for all discovered project content
     */
    public Set<URI> getProjectUris() {
      return projectUris;
    }
  }

}
