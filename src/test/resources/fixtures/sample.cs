using System;
using System.Collections.Generic;
using System.Threading.Tasks;

namespace MyApp.Services
{
    public interface IUserRepository
    {
        Task<User> GetByIdAsync(int id);
        Task<IEnumerable<User>> GetAllAsync();
        Task<User> SaveAsync(User user);
    }

    public enum UserRole
    {
        Admin,
        Editor,
        Viewer
    }

    public class UserService
    {
        private readonly IUserRepository _repository;

        public UserService(IUserRepository repository)
        {
            _repository = repository;
        }

        public async Task<User> GetUserAsync(int id)
        {
            return await _repository.GetByIdAsync(id);
        }

        public async Task<IEnumerable<User>> GetAllUsersAsync()
        {
            return await _repository.GetAllAsync();
        }

        public async Task<User> UpdateUserAsync(User user)
        {
            return await _repository.SaveAsync(user);
        }
    }
}
