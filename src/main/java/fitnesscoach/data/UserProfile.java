package fitnesscoach.data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class UserProfile
{
	private static final int MIN_AGE = 13;
	private static final int MAX_AGE = 100;
	private static final double MIN_HEIGHT = 100;
	private static final double MAX_HEIGHT = 250;
	private static final double MIN_WEIGHT = 30;
	private static final double MAX_WEIGHT = 300;
	private static final int MIN_CALORIES = 1200;

	private int age;
	private double heightCm;
	private double weightKg;
	private String experience;
	private String goal;
	private List<String> availableDays;
	private int minutesPerWorkout;
	private List<String> equipment;
	private List<String> boundaries;
	private Map<LocalDate, Double> weightHistory;


	public UserProfile(int age, double heightCm, double weightKg, String experience)
	{
		this.age = age;
		this.heightCm = heightCm;
		this.weightKg = weightKg;
		this.experience = experience;
		this.goal = "";
		this.availableDays = new ArrayList<>();
		this.minutesPerWorkout = 0;
		this.equipment = new ArrayList<>();
		this.boundaries = new ArrayList<>();
		this.weightHistory = new TreeMap<>();
	}


	public boolean checkBasicInfo()
	{
		if (age < MIN_AGE || age > MAX_AGE)
		{
			return false;
		}
		if (heightCm < MIN_HEIGHT || heightCm > MAX_HEIGHT)
		{
			return false;
		}
		if (weightKg < MIN_WEIGHT || weightKg > MAX_WEIGHT)
		{
			return false;
		}
		if (experience == null)
		{
			return false;
		}
		if (experience.equalsIgnoreCase("beginner") || experience.equalsIgnoreCase("intermediate"))
		{
			return true;
		}
		return false;
	}


    // boundaries from the AI start with a kind, like "goal: build muscle" or "days: monday, friday",
    // so the matching field is filled in too (F01)
    public void addBoundary(String boundary) {
        String lower = boundary.toLowerCase();
        String value = "";
        if (boundary.contains(":")) {
            value = boundary.substring(boundary.indexOf(":") + 1).trim();
        }

        if (lower.startsWith("goal:")) {
            goal = value;
        } else if (lower.startsWith("days:")) {
            for (String day : value.replace(" and ", ", ").split(",")) { // "monday and friday" works too
                day = day.trim().toLowerCase();
                if (isDayName(day) && !availableDays.contains(day)) {
                    availableDays.add(day);
                }
            }
        } else if (lower.startsWith("minutes:")) {
            int minutes = firstNumber(value);
            if (minutes > 0) {
                minutesPerWorkout = minutes;
            }
        } else if (lower.startsWith("equipment:")) {
            for (String item : value.replace(" and ", ", ").split(",")) {
                item = item.trim().toLowerCase();
                if (item.endsWith("s")) {
                    item = item.substring(0, item.length() - 1); // dumbbells becomes dumbbell
                }
                if (!item.isEmpty() && !equipment.contains(item)) {
                    equipment.add(item);
                }
            }
        }

        if (!boundaries.contains(boundary)) {
            boundaries.add(boundary);
        }
    }


	public void addBodyWeight(double weightKg)
	{
		if (weightKg < MIN_WEIGHT || weightKg > MAX_WEIGHT)
		{
			throw new IllegalArgumentException("Weight must be between " + MIN_WEIGHT + " and " + MAX_WEIGHT + " kg");
		}
		this.weightKg = weightKg;
		weightHistory.put(LocalDate.now(), weightKg);
	}


	public Map<String, Integer> calculateTargets()
	{
		double bmr = 10 * weightKg + 6.25 * heightCm - 5 * age - 78; //average of the male and female formulas

		double activity = 1.2;
		int days = availableDays.size();
		if (days >= 6)
		{
			activity = 1.725;
		}
		else if (days >= 4)
		{
			activity = 1.55;
		}
		else if (days >= 2)
		{
			activity = 1.375;
		}

		double total = bmr * activity;
		if (goal.toLowerCase().contains("lose"))
		{
			total -= 500;
		}
		else if (goal.toLowerCase().contains("muscle") || goal.toLowerCase().contains("gain"))
		{
			total += 300;
		}

		int calories = (int) Math.round(total);
		if (calories < MIN_CALORIES)
		{
			calories = MIN_CALORIES; //never go below a safe minimum
		}

		int protein = (int) Math.round(1.8 * weightKg);
		int fat = (int) Math.round(calories * 0.25 / 9);
		int carbs = (int) Math.round((calories - protein * 4 - fat * 9) / 4.0);

		HashMap<String, Integer> targets = new HashMap<>();
		targets.put("calories", calories);
		targets.put("protein", protein);
		targets.put("carbs", carbs);
		targets.put("fat", fat);
		return targets;
	}


	public int getAge()
	{
		return age;
	}


	public double getHeightCm()
	{
		return heightCm;
	}


	public double getWeightKg()
	{
		return weightKg;
	}


	public String getExperience()
	{
		return experience;
	}


	public String getGoal()
	{
		return goal;
	}


	public void setGoal(String goal)
	{
		this.goal = goal;
	}


	public List<String> getAvailableDays()
	{
		return availableDays;
	}


	public int getMinutesPerWorkout()
	{
		return minutesPerWorkout;
	}


	public void setMinutesPerWorkout(int minutesPerWorkout)
	{
		this.minutesPerWorkout = minutesPerWorkout;
	}


	public List<String> getEquipment()
	{
		return equipment;
	}


	public List<String> getBoundaries()
	{
		return boundaries;
	}


	public Map<LocalDate, Double> getWeightHistory()
	{
		return weightHistory;
	}


    private boolean isDayName(String day) {
        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
            if (dayOfWeek.toString().equalsIgnoreCase(day)) {
                return true;
            }
        }
        return false;
    }

    // the first whole number in text like "45" or "30-45 minutes", or 0 when there is none
    private int firstNumber(String text) {
        String digits = "";
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) {
                digits += c;
            } else if (!digits.isEmpty()) {
                break;
            }
        }
        if (digits.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(digits);
    }
}
