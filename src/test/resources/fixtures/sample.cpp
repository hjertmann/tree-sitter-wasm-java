#include <vector>
#include <string>
#include <stdexcept>

class Stack {
private:
    std::vector<int> data;

public:
    void push(int value) {
        data.push_back(value);
    }

    int pop() {
        if (data.empty()) {
            throw std::runtime_error("Stack is empty");
        }
        int top = data.back();
        data.pop_back();
        return top;
    }

    int peek() const {
        if (data.empty()) {
            throw std::runtime_error("Stack is empty");
        }
        return data.back();
    }

    bool empty() const {
        return data.empty();
    }

    size_t size() const {
        return data.size();
    }
};

int sum(const std::vector<int>& values) {
    int total = 0;
    for (int v : values) total += v;
    return total;
}
