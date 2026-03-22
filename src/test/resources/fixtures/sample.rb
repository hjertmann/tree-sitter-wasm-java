class Calculator
  def initialize(precision = 2)
    @precision = precision
  end

  def add(a, b)
    (a + b).round(@precision)
  end

  def subtract(a, b)
    (a - b).round(@precision)
  end

  def multiply(a, b)
    (a * b).round(@precision)
  end

  def divide(a, b)
    raise ArgumentError, "Division by zero" if b == 0
    (a.to_f / b).round(@precision)
  end

  def self.create(precision = 2)
    new(precision)
  end
end
