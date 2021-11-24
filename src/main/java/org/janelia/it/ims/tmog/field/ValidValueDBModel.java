package org.janelia.it.ims.tmog.field;

import org.janelia.it.ims.tmog.config.preferences.FieldDefault;
import org.janelia.it.ims.tmog.config.preferences.FieldDefaultSet;
import org.janelia.it.ims.tmog.target.Target;

import org.apache.log4j.Logger;

import javax.swing.table.TableModel;
import javax.swing.text.PlainDocument;
import javax.swing.text.BadLocationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * This model tries to split a field's contents into tokens, and then validate each token
 * against a list of valid values, which is read from a specified text file.
 *
 * @author Andy Stoychev
 */
public class ValidValueDBModel extends PlainDocument implements DataField, DefaultValueModel {

    /** The logger for this class. */
    private static final Logger LOG = Logger.getLogger(ValidValueDBModel.class);

    private String displayName;
    private boolean isRequired;
    private ArrayList<ValidValue> validValues;
    private String errorMessage;
    private String prefix;
    private String suffix;
    private boolean isCopyable;
    private boolean markedForTask;
    private boolean sharedForAllSessionFiles;
    private DefaultValueList defaultValueList;
    private File DBPath;
    private File configPath;
    private String separator;
    private Pattern validationPattern;
    private ArrayList<String> cachedTokens;

    public ValidValueDBModel() {
        this.isRequired = false;
        this.validValues = new ArrayList<ValidValue>();
        this.isCopyable = true;
        this.markedForTask = true;
        this.sharedForAllSessionFiles = false;
        this.defaultValueList = new DefaultValueList();
    }

    public ValidValueDBModel(ValidValueDBModel instance) {
        this.setText(instance.getFullText());
        this.displayName = instance.displayName;
        this.isRequired = instance.isRequired;
        this.validValues = instance.validValues; // shallow copy should be safe
        this.prefix = instance.prefix;
        this.suffix = instance.suffix;
        this.isCopyable = instance.isCopyable;
        this.markedForTask = instance.markedForTask;
        this.sharedForAllSessionFiles = instance.sharedForAllSessionFiles;
        this.defaultValueList = instance.defaultValueList; // shallow copy is ok
        this.DBPath = instance.DBPath;
        this.configPath = instance.configPath;
        this.separator = instance.separator;
        this.validationPattern = instance.validationPattern;
        this.cachedTokens = instance.cachedTokens; // shallow copy
    }

    public int size() {
        return this.validValues.size();
    }

    public DefaultValueList getDefaultValueList() {
        return this.defaultValueList;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public Integer getDisplayWidth() {
        return null;
    }

    public boolean isEditable() {
        return true;
    }

    public boolean isVisible() {
        return true;
    }

    public boolean isCopyable() {
        return this.isCopyable;
    }

    public boolean isMarkedForTask() {
        return this.markedForTask;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setMarkedForTask(boolean markedForTask) {
        this.markedForTask = markedForTask;
    }

    public boolean isSharedForAllSessionFiles() {
        return this.sharedForAllSessionFiles;
    }

    public void setSharedForAllSessionFiles(boolean sharedForAllSessionFiles) {
        this.sharedForAllSessionFiles = sharedForAllSessionFiles;
    }

    public void setConfigPath(File configPath) {
        this.configPath = configPath;
        if (!this.DBPath.isAbsolute()) {
            this.DBPath = new File(
                this.configPath.getParent()
                + File.separator
                + this.DBPath.getPath()
            );
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setDBPath(String DBPath) {
        this.DBPath = new File(DBPath);
    }

    public void readValidValuesFromDBFile() {
        if (!this.validValues.isEmpty()) {
            this.validValues.clear();
        }
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(this.DBPath));
            String line;
            while ((line = in.readLine()) != null) {
                this.validValues.add(new ValidValue(line));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Failed to load valid values from '"
                + this.DBPath.getAbsolutePath()
                + "'. Please verify the DB path is accurate.",
                e
            );
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    LOG.error("Failed to close file input stream, ignoring error.", e);
                }
            }
        }
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }

    public void setValidationPattern(String pattern) {
        this.validationPattern = Pattern.compile(pattern);
    }

    public void addDefaultValue(DefaultValue defaultValue) {
        this.defaultValueList.add(defaultValue);
    }

    public ValidValueDBModel getNewInstance(boolean isCloneRequired) {
        ValidValueDBModel instance = this;
        if (isCloneRequired || (! this.sharedForAllSessionFiles)) {
            instance = new ValidValueDBModel(this);
        }
        return instance;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }

    public String getCoreValue() {
        String coreValue = this.getFullText();
        if (coreValue == null) {
            coreValue = "";
        }
        return coreValue;
    }

    public String getFileNameValue() {
        String fileNameValue = this.getFullText();
        if ((fileNameValue != null) && (fileNameValue.length() > 0)) {
            StringBuilder sb = new StringBuilder(64);
            if (this.prefix != null) {
                sb.append(this.prefix);
            }
            sb.append(fileNameValue);
            if (this.suffix != null) {
                sb.append(this.suffix);
            }
            fileNameValue = sb.toString();
        } else {
            fileNameValue = "";
        }
        return fileNameValue;
    }

    @SuppressWarnings("unchecked")
    public void setCachedTokens(ArrayList<String> newTokens) {
        this.cachedTokens = (ArrayList<String>) newTokens.clone();
    }

    public ArrayList<String> tokenizeText(String text) {
        ArrayList<String> tokens = new ArrayList<String>();
        if ((this.separator == null) || (this.separator.length() == 0)) {
            tokens.add(text);
        } else {
            String[] splits = text.split(this.separator);
            for (String split : splits) {
                tokens.add(split);
            }
        }
        return tokens;
    }

    public boolean validateTokens(ArrayList<String> tokens) {
        this.errorMessage = "";

        if (tokens.isEmpty()) {
            this.errorMessage = "Cannot validate an empty list of tokens.";
            LOG.warn(this.errorMessage);
            return false;
        }

        boolean areTokensValid = false;
        for (String token : tokens) {
            String textToValidate = null;
            if (this.validationPattern != null) {
                Matcher m = this.validationPattern.matcher(token);
                if (m.find()) {
                    textToValidate = m.group();
                } else {
                    this.errorMessage = "Validation pattern '"
                                      + this.validationPattern
                                      + "' doesn't match token '"
                                      + token
                                      + "'.";
                    LOG.error(this.errorMessage);
                    return false;
                }
            } else {
                textToValidate = token;
            }

            boolean isCurrentTokenValid = false;
            for (ValidValue validValue : this.validValues) {
                if (textToValidate.equals(validValue.getValue())) {
                    isCurrentTokenValid = true;
                    break;
                }
            }

            if (isCurrentTokenValid) {
                areTokensValid = true;
            } else {
                areTokensValid = false;
                break;
            }
        }

        if (!areTokensValid) {
            this.errorMessage =
                "This field should contain values matching those in '" + this.DBPath + "'.";
        }

        return areTokensValid;
    }

    public void sortCachedTokens() {
        Collections.sort(this.cachedTokens);

        StringBuilder sb = new StringBuilder(this.cachedTokens.get(0));
        for (int i = 1; i < this.cachedTokens.size(); i++) {
            sb.append(this.separator);
            sb.append(this.cachedTokens.get(i));
        }
        this.setText(sb.toString());
    }

    public boolean verify() {
        boolean isValid = false;
        this.errorMessage = "";

        if ((this.cachedTokens != null) && (!this.cachedTokens.isEmpty())) {
            isValid = this.validateTokens(this.cachedTokens);
        } else if (this.isRequired) {
            this.errorMessage = "Please enter a value for this required field.";
            isValid = false;
        } else {
            isValid = true;
        }

        return isValid;
    }

    /**
     * Initializes this field's value based upon the specified target.
     *
     * @param  target  the target being processed.
     */
    public void initializeValue(Target target) {
        final String defaultValue = this.defaultValueList.getValue(target);
        this.applyValue(defaultValue);
    }

    @Override
    public void applyValue(String newValue) {
        if ((newValue == null) || (newValue.length() == 0)) {
            return;
        }

        ArrayList<String> newTokens = this.tokenizeText(newValue);
        boolean isValid = this.validateTokens(newTokens);
        if (isValid) {
            this.setCachedTokens(newTokens);
            this.sortCachedTokens();
        }
    }

    public void applyDefault(FieldDefaultSet defaultSet) {
        final FieldDefault fieldDefault =
                defaultSet.getFieldDefault(displayName);
        if (fieldDefault != null) {
            final String value = fieldDefault.getValue();
            this.applyValue(value);
        }
    }

    public void addAsDefault(FieldDefaultSet defaultSet) {
        final String coreValue = this.getCoreValue();
        if (coreValue.length() > 0) {
            FieldDefault fieldDefault = new FieldDefault();
            fieldDefault.setName(displayName);
            fieldDefault.setValue(coreValue);
            defaultSet.addFieldDefault(fieldDefault);
        }
    }

    public boolean isRequired() {
        return this.isRequired;
    }

    public String getPrefix() {
        return this.prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getSuffix() {
        return this.suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setRequired(boolean required) {
        if (this.isRequired != required) {
            this.isRequired = required;
            if (this.isRequired) {
                this.validValues.remove(ValidValue.NONE);
            } else {
                this.validValues.add(ValidValue.NONE);
            }
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setCopyable(boolean copyable) {
        this.isCopyable = copyable;
    }

    public int getSize() {
        return this.validValues.size();
    }

    public String getFullText() {
        String text;
        try {
            text = this.getText(0, this.getLength());
        } catch (BadLocationException e) {
            text = null;
        }
        return text;
    }

    public void setText(String t) {
        try {
            this.replace(0, this.getLength(), t, null);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return this.getFullText();
    }

}
